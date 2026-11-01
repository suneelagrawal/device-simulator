
package device.simulator.dto;

public record DeviceCommandResponse(
        String commandKey,
        String status,
        String reason
) {}
