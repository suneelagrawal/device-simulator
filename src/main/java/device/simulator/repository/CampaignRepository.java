
package device.simulator.repository;

import device.simulator.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository
        extends JpaRepository<Campaign, String> {
}
