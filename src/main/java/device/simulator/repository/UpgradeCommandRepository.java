
package device.simulator.repository;

import device.simulator.entity.UpgradeCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UpgradeCommandRepository
        extends JpaRepository<UpgradeCommand, String> {

    List<UpgradeCommand> findByCampaignIdAndDeviceId(
            String campaignId, String deviceId);
}
