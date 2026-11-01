
package device.simulator.controller;

import device.simulator.dto.DeviceInformRequest;
import device.simulator.dto.DeviceCommandResponse;
import device.simulator.service.DeviceUpgradeService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceUpgradeService deviceUpgradeService;

    public DeviceController(DeviceUpgradeService deviceUpgradeService) {
        this.deviceUpgradeService = deviceUpgradeService;
    }

    @PostMapping("/{deviceId}/inform")
    public Map<String, Object> inform(
            @PathVariable String deviceId,
            @RequestBody DeviceInformRequest request) {

        return deviceUpgradeService.handleInform(deviceId, request);
    }

    @PostMapping("/{deviceId}/command-response")
    @ResponseStatus(HttpStatus.OK)
    public void commandResponse(
            @PathVariable String deviceId,
            @RequestBody DeviceCommandResponse request) {

        deviceUpgradeService.handleCommandResponse(deviceId, request);
    }
}
