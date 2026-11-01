
package device.simulator.repository;

import device.simulator.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, String> {

    List<Campaign> findByStatusOrderByCreatedAtAsc(String status);
}
