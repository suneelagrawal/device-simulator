package device.simulator.service;

import device.simulator.entity.Campaign;
import device.simulator.entity.CampaignDevice;
import device.simulator.repository.CampaignDeviceRepository;
import device.simulator.repository.CampaignRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CampaignRecoveryService {

    private final CampaignRepository campaignRepository;
    private final CampaignDeviceRepository deviceRepository;

    private final int maxNotificationAttempts;
    private final long notificationTimeoutSeconds;
    private final int minimumSampleSize;

    public CampaignRecoveryService(
            CampaignRepository campaignRepository,
            CampaignDeviceRepository deviceRepository,
            @Value("${campaign.max-notification-attempts:3}")
            int maxNotificationAttempts,
            @Value("${campaign.notification-timeout-seconds:15}")
            long notificationTimeoutSeconds,
            @Value("${campaign.failure-min-sample-size:5}")
            int minimumSampleSize) {

        this.campaignRepository = campaignRepository;
        this.deviceRepository = deviceRepository;
        this.maxNotificationAttempts = maxNotificationAttempts;
        this.notificationTimeoutSeconds = notificationTimeoutSeconds;
        this.minimumSampleSize = minimumSampleSize;
    }

    @Transactional
    public List<String> recoverDevices(String campaignId) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow();

        if (!List.of("RUNNING", "HALTED")
                .contains(campaign.getStatus())) {
            return List.of();
        }

        Instant now = Instant.now();
        List<String> retries = new ArrayList<>();

        for (CampaignDevice device :
                deviceRepository.findByCampaignId(campaignId)) {

            Instant deadline = device.getDeadlineAt();

            if (deadline == null || deadline.isAfter(now)) {
                continue;
            }

            switch (device.getState()) {

                case "NOTIFIED" -> {
                    if ("RUNNING".equals(campaign.getStatus())
                            && device.getAttempts() <
                                    maxNotificationAttempts) {

                        device.setAttempts(device.getAttempts() + 1);
                        device.setDeadlineAt(now.plusSeconds(
                                notificationTimeoutSeconds));
                        deviceRepository.save(device);
                        retries.add(device.getDeviceId());
                    } else {
                        timeOut(device, "Gateway did not send Inform");
                    }
                }

                case "DOWNLOAD_SENT" ->
                        timeOut(device, "Command acknowledgment timeout");

                case "ACCEPTED" ->
                        timeOut(device, "Upgrade completion timeout");

                default -> {
                    // Terminal or PENDING devices need no recovery.
                }
            }
        }

        return retries;
    }

    @Transactional
    public void evaluateCampaign(String campaignId) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow();

        if (!List.of("RUNNING", "HALTED")
                .contains(campaign.getStatus())) {
            return;
        }

        List<CampaignDevice> devices =
                deviceRepository.findByCampaignId(campaignId);

        long succeeded = devices.stream()
                .filter(d -> "SUCCEEDED".equals(d.getState()))
                .count();

        long failed = devices.stream()
                .filter(d -> "FAILED".equals(d.getState()))
                .count();

        long timedOut = devices.stream()
                .filter(d -> "TIMED_OUT".equals(d.getState()))
                .count();

        long terminal = succeeded + failed + timedOut;

        // Finish only when every device is terminal.
        if (terminal == devices.size()) {
            campaign.setStatus("COMPLETED");
            campaignRepository.save(campaign);
            return;
        }

        // Preserve an existing halt decision.
        if ("HALTED".equals(campaign.getStatus())) {
            return;
        }

        if (terminal >= minimumSampleSize) {

            double rate = (failed + timedOut) * 100.0 / terminal;

            if (rate > campaign.getFailureThresholdPercent()) {
                campaign.setStatus("HALTED");
                campaignRepository.save(campaign);
            }
        }
    }

    private void timeOut(CampaignDevice device, String reason) {
        device.setState("TIMED_OUT");
        device.setLastError(reason);
        device.setDeadlineAt(null);
        deviceRepository.save(device);
    }
}
