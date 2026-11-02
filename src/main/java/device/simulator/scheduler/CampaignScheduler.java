package device.simulator.scheduler;

import device.simulator.entity.Campaign;
import device.simulator.repository.CampaignRepository;
import device.simulator.service.CampaignDispatchService;
import device.simulator.service.CampaignRecoveryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Component
public class CampaignScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(CampaignScheduler.class);

    private final CampaignRepository campaignRepository;
    private final CampaignDispatchService dispatchService;
    private final CampaignRecoveryService recoveryService;
    private final RestClient simulatorClient;

    public CampaignScheduler(
            CampaignRepository campaignRepository,
            CampaignDispatchService dispatchService,
            CampaignRecoveryService recoveryService,
            @Value("${simulator.base-url}") String simulatorBaseUrl) {

        this.campaignRepository = campaignRepository;
        this.dispatchService = dispatchService;
        this.recoveryService = recoveryService;

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.simulatorClient = RestClient.builder()
                .baseUrl(simulatorBaseUrl)
                .requestFactory(factory)
                .build();
    }

    @Scheduled(fixedDelayString =
            "${campaign.scheduler-delay-ms:2000}")
    public void processCampaigns() {

        // Also process HALTED campaigns so in-flight devices
        // can reach terminal states or expire.
        List<Campaign> campaigns = campaignRepository.findAll();

        for (Campaign campaign : campaigns) {

            if (!List.of("RUNNING", "HALTED")
                    .contains(campaign.getStatus())) {
                continue;
            }

            try {
                List<String> retries =
                        recoveryService.recoverDevices(campaign.getId());

                recoveryService.evaluateCampaign(campaign.getId());

                Campaign current = campaignRepository
                        .findById(campaign.getId())
                        .orElseThrow();

                if (!"RUNNING".equals(current.getStatus())) {
                    continue;
                }

                // Retry only connection requests, never DOWNLOAD.
                for (String deviceId : retries) {
                    sendConnectionRequest(campaign.getId(), deviceId);
                }

                List<String> newlyReserved =
                        dispatchService.reserveDevices(campaign.getId());

                for (String deviceId : newlyReserved) {
                    sendConnectionRequest(campaign.getId(), deviceId);
                }

            } catch (Exception e) {
                log.error("Scheduler failed for campaign {}",
                        campaign.getId(), e);
            }
        }
    }

    private void sendConnectionRequest(
            String campaignId, String deviceId) {

        try {
            var response = simulatorClient.post()
                    .uri("/devices/{deviceId}/connection-request",
                            deviceId)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "Connection request: campaign={}, device={}, status={}",
                    campaignId, deviceId, response.getStatusCode());

        } catch (Exception e) {
            log.warn(
                    "Connection request failed: campaign={}, device={}, error={}",
                    campaignId, deviceId, e.getMessage());
            // NOTIFIED is retained until its deadline expires.
        }
    }
}
