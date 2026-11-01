
package device.simulator.service;

import device.simulator.entity.Campaign;
import device.simulator.entity.CampaignDevice;
import device.simulator.repository.CampaignRepository;
import device.simulator.repository.CampaignDeviceRepository;

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

    public CampaignDispatchService(
            CampaignRepository campaignRepository,
            CampaignDeviceRepository deviceRepository) {

        this.campaignRepository = campaignRepository;
        this.deviceRepository = deviceRepository;
    }

    // Single-instance reservation logic.
    // Distributed deployments require DB-level coordination.
    @Transactional
    public synchronized List<String> reserveDevices(String campaignId) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow();

        if (!"RUNNING".equals(campaign.getStatus())) {
            return List.of();
        }

        List<CampaignDevice> devices =
                deviceRepository.findByCampaignId(campaignId);

        long inFlight = devices.stream()
                .filter(d -> ACTIVE_STATES.contains(d.getState()))
                .count();

        long availableSlots = Math.max(
                0, (long) campaign.getMaxConcurrent() - inFlight);

        List<String> selected = new ArrayList<>();

        if (availableSlots == 0) {
            return selected;
        }

        for (CampaignDevice device : devices) {

            if (selected.size() >= availableSlots) {
                break;
            }

            if (!"PENDING".equals(device.getState())) {
                continue;
            }

            // Avoid overlapping active upgrades on one gateway.
            if (deviceRepository.existsByDeviceIdAndStateIn(
                    device.getDeviceId(), ACTIVE_STATES)) {
                continue;
            }

            device.setState("NOTIFIED");
            device.setAttempts(device.getAttempts() + 1);
            device.setDeadlineAt(Instant.now().plusSeconds(60));

            deviceRepository.save(device);
            selected.add(device.getDeviceId());
        }

        return selected;
    }
}
