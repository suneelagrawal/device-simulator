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
public class CampaignDispatchService {

    private static final List<String> ACTIVE_STATES =
            List.of("NOTIFIED", "DOWNLOAD_SENT", "ACCEPTED");

    private final CampaignRepository campaignRepository;
    private final CampaignDeviceRepository deviceRepository;
    private final long notificationTimeoutSeconds;

    public CampaignDispatchService(
            CampaignRepository campaignRepository,
            CampaignDeviceRepository deviceRepository,
            @Value("${campaign.notification-timeout-seconds:15}")
            long notificationTimeoutSeconds) {

        this.campaignRepository = campaignRepository;
        this.deviceRepository = deviceRepository;
        this.notificationTimeoutSeconds = notificationTimeoutSeconds;
    }

    @Transactional
    public synchronized List<String> reserveDevices(String campaignId) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow();

        if (!"RUNNING".equals(campaign.getStatus())) {
            return List.of();
        }

        List<CampaignDevice> devices =
                deviceRepository.findByCampaignId(campaignId);

        long activeCount = devices.stream()
                .filter(d -> ACTIVE_STATES.contains(d.getState()))
                .count();

        int available = (int) Math.max(0,
                (long) campaign.getMaxConcurrent() - activeCount);

        List<String> reserved = new ArrayList<>();

        for (CampaignDevice device : devices) {

            if (reserved.size() >= available) {
                break;
            }

            if (!"PENDING".equals(device.getState())) {
                continue;
            }

            if (deviceRepository.existsByDeviceIdAndStateIn(
                    device.getDeviceId(), ACTIVE_STATES)) {
                continue;
            }

            device.setState("NOTIFIED");
            device.setAttempts(device.getAttempts() + 1);
            device.setDeadlineAt(
                    Instant.now().plusSeconds(notificationTimeoutSeconds));

            deviceRepository.save(device);
            reserved.add(device.getDeviceId());
        }

        return reserved;
    }
}
