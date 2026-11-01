
package device.simulator.dto;

import java.util.Map;

public record CampaignStatusResponse(
        String campaignId,
        String status,
        String targetVersion,
        long totalDevices,
        Map<String, Long> deviceCounts,
        double failureRatePercent
) {}
