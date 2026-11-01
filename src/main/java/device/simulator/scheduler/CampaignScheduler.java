
package device.simulator.scheduler;

import device.simulator.entity.Campaign;
import device.simulator.repository.CampaignRepository;
import device.simulator.service.CampaignDispatchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class CampaignScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(CampaignScheduler.class);

    private final CampaignRepository campaignRepository;
    private final CampaignDispatchService dispatchService;
    private final RestClient simulatorClient;

    public CampaignScheduler(
            CampaignRepository campaignRepository,
            CampaignDispatchService dispatchService,
            @Value("${simulator.base-url}") String simulatorBaseUrl) {

        this.campaignRepository = campaignRepository;
        this.dispatchService = dispatchService;
        this.simulatorClient = RestClient.create(simulatorBaseUrl);
    }

    @Scheduled(fixedDelayString = "${campaign.scheduler-delay-ms:2000}")
    public void processCampaigns() {

        List<Campaign> campaigns =
                campaignRepository.findByStatusOrderByCreatedAtAsc(
                        "RUNNING");

        for (Campaign campaign : campaigns) {

            List<String> devices;

            try {
                devices = dispatchService.reserveDevices(
                        campaign.getId());
            } catch (Exception e) {
                log.error("Unable to reserve devices for campaign {}",
                        campaign.getId(), e);
                continue;
            }

            for (String deviceId : devices) {

                try {
                    ResponseEntity<Void> response = simulatorClient
                            .post()
                            .uri("/devices/{deviceId}/connection-request",
                                    deviceId)
                            .retrieve()
                            .toBodilessEntity();

                    log.info(
                            "Connection request: campaign={}, device={}, status={}",
                            campaign.getId(),
                            deviceId,
                            response.getStatusCode());

                } catch (Exception e) {
                    log.error(
                            "Connection request failed: campaign={}, device={}",
                            campaign.getId(),
                            deviceId,
                            e);
                    // Keep NOTIFIED to avoid blindly resending.
                    // Timeout/recovery logic will handle this later.
                }
            }
        }
    }
}
