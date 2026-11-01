
package device.simulator.service;

import device.simulator.dto.DeviceInformRequest;
import device.simulator.dto.DeviceCommandResponse;
import device.simulator.entity.Campaign;
import device.simulator.entity.CampaignDevice;
import device.simulator.entity.UpgradeCommand;
import device.simulator.repository.CampaignRepository;
import device.simulator.repository.CampaignDeviceRepository;
import device.simulator.repository.UpgradeCommandRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceUpgradeService {

    private final CampaignRepository campaignRepository;
    private final CampaignDeviceRepository deviceRepository;
    private final UpgradeCommandRepository commandRepository;

    public DeviceUpgradeService(
            CampaignRepository campaignRepository,
            CampaignDeviceRepository deviceRepository,
            UpgradeCommandRepository commandRepository) {
        this.campaignRepository = campaignRepository;
        this.deviceRepository = deviceRepository;
        this.commandRepository = commandRepository;
    }

    private boolean terminal(String state) {
        return List.of("SUCCEEDED", "FAILED", "TIMED_OUT")
                .contains(state);
    }

    @Transactional
    public Map<String, Object> handleInform(
            String deviceId, DeviceInformRequest request) {

        if (!deviceId.equals(request.deviceId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Device ID mismatch");
        }

        // A completion event is correlated by commandKey,
        // not by whichever campaign happens to be running.
        if ("TRANSFER_COMPLETE".equals(request.event())) {
            processTransferComplete(deviceId, request);
            return Map.of("commands", List.of());
        }

        for (Campaign campaign : campaignRepository.findAll()) {

            Optional<CampaignDevice> found =
                    deviceRepository.findByCampaignIdAndDeviceId(
                            campaign.getId(), deviceId);

            if (found.isEmpty()) {
                continue;
            }

            CampaignDevice device = found.get();

            if (terminal(device.getState())) {
                continue;
            }

            // A BOOT reporting target firmware confirms success.
            if ("BOOT".equals(request.event())
                    && List.of("DOWNLOAD_SENT", "ACCEPTED")
                            .contains(device.getState())
                    && campaign.getTargetVersion()
                            .equals(request.firmwareVersion())) {

                device.setState("SUCCEEDED");
                device.setCurrentVersion(request.firmwareVersion());
                deviceRepository.save(device);

                for (UpgradeCommand command :
                        commandRepository.findByCampaignIdAndDeviceId(
                                campaign.getId(), deviceId)) {

                    if (!"FAILED".equals(command.getStatus())) {
                        command.setStatus("CONFIRMED_BY_BOOT");
                        command.setCompletedAt(Instant.now());
                        commandRepository.save(command);
                    }
                }

                return Map.of("commands", List.of());
            }

            // Only issue a DOWNLOAD when the gateway has
            // been selected and notified by the scheduler.
            if (!"RUNNING".equals(campaign.getStatus())
                    || !"NOTIFIED".equals(device.getState())) {
                continue;
            }

            String key = UUID.randomUUID().toString();

            UpgradeCommand command = new UpgradeCommand();
            command.setCommandKey(key);
            command.setCampaignId(campaign.getId());
            command.setDeviceId(deviceId);
            command.setTargetVersion(campaign.getTargetVersion());
            command.setStatus("SENT");

            commandRepository.save(command);

            device.setState("DOWNLOAD_SENT");
            deviceRepository.save(device);

            Map<String, String> download = Map.of(
                    "type", "DOWNLOAD",
                    "commandKey", key,
                    "url", campaign.getImageUrl(),
                    "targetVersion", campaign.getTargetVersion()
            );

            return Map.of("commands", List.of(download));
        }

        return Map.of("commands", List.of());
    }

    @Transactional
    public void handleCommandResponse(
            String deviceId, DeviceCommandResponse response) {

        UpgradeCommand command = commandRepository
                .findById(response.commandKey())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown commandKey"));

        if (!deviceId.equals(command.getDeviceId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Device ID mismatch");
        }

        CampaignDevice device = deviceRepository
                .findByCampaignIdAndDeviceId(
                        command.getCampaignId(), deviceId)
                .orElseThrow();

        // Ignore duplicate or late acknowledgments
        // after a terminal device outcome.
        if (terminal(device.getState())) {
            return;
        }

        if ("ACCEPTED".equals(response.status())) {
            if ("DOWNLOAD_SENT".equals(device.getState())) {
                command.setStatus("ACCEPTED");
                device.setState("ACCEPTED");
            }
        } else if ("REJECTED".equals(response.status())) {
            command.setStatus("REJECTED");
            device.setState("FAILED");
            device.setLastError(response.reason());
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid command status");
        }

        commandRepository.save(command);
        deviceRepository.save(device);
    }

    private void processTransferComplete(
            String deviceId, DeviceInformRequest request) {

        if (request.transferComplete() == null
                || request.transferComplete().commandKey() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing transferComplete commandKey");
        }

        var event = request.transferComplete();

        UpgradeCommand command = commandRepository
                .findById(event.commandKey())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown commandKey"));

        if (!deviceId.equals(command.getDeviceId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Device ID mismatch");
        }

        CampaignDevice device = deviceRepository
                .findByCampaignIdAndDeviceId(
                        command.getCampaignId(), deviceId)
                .orElseThrow();

        if (terminal(device.getState())) {
            return;
        }

        if (event.faultCode() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Missing faultCode");
        }

        command.setFaultCode(event.faultCode());
        command.setCompletedAt(Instant.now());

        if (event.faultCode() == 0) {
            command.setStatus("SUCCEEDED");
            device.setState("SUCCEEDED");
            device.setCurrentVersion(request.firmwareVersion());
        } else {
            command.setStatus("FAILED");
            device.setState("FAILED");
            device.setLastError(
                    "TransferComplete fault: " + event.faultCode());
        }

        commandRepository.save(command);
        deviceRepository.save(device);
    }
}
