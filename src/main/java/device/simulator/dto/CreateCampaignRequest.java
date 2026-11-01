
package device.simulator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CreateCampaignRequest(
        @NotBlank String targetVersion,
        @NotBlank String imageUrl,
        @NotNull @Valid DeviceFilter deviceFilter,
        @Min(1) int maxConcurrent,
        @DecimalMin("0.0") @DecimalMax("100.0")
        double failureThresholdPercent
) {
    public record DeviceFilter(
            @NotBlank String currentVersion
    ) {}
}
