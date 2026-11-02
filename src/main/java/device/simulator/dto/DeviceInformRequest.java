
package device.simulator.dto;

public record DeviceInformRequest(
        String deviceId,
        String firmwareVersion,
        String event,
        TransferComplete transferComplete
) {
    public record TransferComplete(
            String commandKey,
            Integer faultCode,
            Object startTime,
            Object completeTime
    ) {}
}
