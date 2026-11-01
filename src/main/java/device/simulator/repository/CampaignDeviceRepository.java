
package device.simulator.repository;

import device.simulator.entity.CampaignDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignDeviceRepository
        extends JpaRepository<CampaignDevice, Long> {

    List<CampaignDevice> findByCampaignId(String campaignId);

    Optional<CampaignDevice> findByCampaignIdAndDeviceId(
            String campaignId, String deviceId);

    long countByCampaignIdAndState(
            String campaignId, String state);
}
