package device.simulator.service;

import device.simulator.dto.DeviceInformRequest;
import device.simulator.dto.DeviceCommandResponse;
import device.simulator.entity.Campaign;
import device.simulator.entity.CampaignDevice;
import device.simulator.entity.UpgradeCommand;
import device.simulator.repository.CampaignRepository;
import device.simulator.repository.CampaignDeviceRepository;
import device.simulator.repository.UpgradeCommandRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeviceUpgradeService {

    private final CampaignRepository campaignRepository;
    private final CampaignDeviceRepository deviceRepository;
    private final UpgradeCommandRepository commandRepository;

    private final long acknowledgmentTimeoutSeconds;
    private final long upgradeTimeoutSeconds;

    public DeviceUpgradeService(
            CampaignRepository campaignRepository,
            CampaignDeviceRepository deviceRepository,
            UpgradeCommandRepository commandRepository,
            @Value("${campaign.acknowledgment-timeout-seconds:20}")
            long acknowledgmentTimeoutSeconds,
            @Value("${campaign.upgrade-timeout-seconds:90}")
            long upgradeTimeoutSeconds) {

        this.campaignRepository = campaignRepository;
        this.deviceRepository = deviceRepository;
        this.commandRepository = commandRepository;
        this.acknowledgmentTimeoutSeconds =
                acknowledgmentTimeoutSeconds;
        this.upgradeTimeoutSeconds = upgradeTimeoutSeconds;
    }

    private boolean terminal(String state) {
        return List.of("SUCCEEDED", "FAILED", "TIMED_OUT")
                .contains(state);
    }

    private CampaignDevice findDevice(
            String campaignId, String deviceId) {

        return deviceRepository
                .findByCampaignIdAndDeviceId(campaignId, deviceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Campaign device not found"));
    }

    @Transactional
    public Map<String, Object> handleInform(
            String deviceId,
            DeviceInformRequest request) {

        if (request == null
                || request.deviceId() == null
                || !deviceId.equals(request.deviceId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Device ID mismatch");
        }

        // Correlate completion events to the exact command.
        if ("TRANSFER_COMPLETE".equals(request.event())) {
            processTransferComplete(deviceId, request);
            return Map.of("commands", List.of());
        }

        // Deterministic order for local single-instance testing.
        List<Campaign> campaigns = campaignRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(
                        Campaign::getCreatedAt,
                        Comparator.nullsLast(
                                Comparator.naturalOrder())))
                .toList();

        for (Campaign campaign : campaigns) {

            var optional = deviceRepository
                    .findByCampaignIdAndDeviceId(
                            campaign.getId(), deviceId);

            if (optional.isEmpty()) {
                continue;
            }

            CampaignDevice device = optional.get();

            // BOOT reporting target firmware is sufficient
            // evidence when a DOWNLOAD was already issued.
            if ("BOOT".equals(request.event())
                    && List.of("DOWNLOAD_SENT", "ACCEPTED")
                            .contains(device.getState())
                    && campaign.getTargetVersion()
                            .equals(request.firmwareVersion())) {

                device.setState("SUCCEEDED");
                device.setCurrentVersion(request.firmwareVersion());
                device.setDeadlineAt(null);
                deviceRepository.save(device);

                for (UpgradeCommand command :
                        commandRepository.findByCampaignIdAndDeviceId(
                                campaign.getId(), deviceId)) {

                    if (!"FAILED".equals(command.getStatus())
                            && !"REJECTED".equals(command.getStatus())) {
                        command.setStatus("CONFIRMED_BY_BOOT");
                        command.setCompletedAt(Instant.now());
                        commandRepository.save(command);
                    }
                }

                return Map.of("commands", List.of());
            }

            // Never issue a command to a terminal device.
            if (terminal(device.getState())) {
                continue;
            }

            // Only a scheduler-reserved gateway may
            // receive DOWNLOAD, even if it sends Inform.
            if (!"RUNNING".equals(campaign.getStatus())
                    || !"NOTIFIED".equals(device.getState())) {
                continue;
            }

            String commandKey = UUID.randomUUID().toString();

            UpgradeCommand command = new UpgradeCommand();
            command.setCommandKey(commandKey);
            command.setCampaignId(campaign.getId());
            command.setDeviceId(deviceId);
            command.setTargetVersion(campaign.getTargetVersion());
            command.setStatus("SENT");

            commandRepository.save(command);

            device.setState("DOWNLOAD_SENT");
            device.setDeadlineAt(Instant.now().plusSeconds(
                    acknowledgmentTimeoutSeconds));
            deviceRepository.save(device);

            return Map.of("commands", List.of(Map.of(
                    "type", "DOWNLOAD",
                    "commandKey", commandKey,
                    "url", campaign.getImageUrl(),
                    "targetVersion", campaign.getTargetVersion()
            )));
        }

        return Map.of("commands", List.of());
    }

    @Transactional
    public void handleCommandResponse(
            String deviceId,
            DeviceCommandResponse response) {

        if (response == null || response.commandKey() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Missing commandKey");
        }

        UpgradeCommand command = commandRepository
                .findById(response.commandKey())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown commandKey"));

        if (!deviceId.equals(command.getDeviceId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Device ID mismatch");
        }

        CampaignDevice device = findDevice(
                command.getCampaignId(), deviceId);

        if (terminal(device.getState())) {
            return;
        }

        if ("ACCEPTED".equals(response.status())) {

            // Repeated ACCEPTED acknowledgments are harmless.
            if ("DOWNLOAD_SENT".equals(device.getState())) {
                command.setStatus("ACCEPTED");
                device.setState("ACCEPTED");
                device.setDeadlineAt(Instant.now().plusSeconds(
                        upgradeTimeoutSeconds));
            }

        } else if ("REJECTED".equals(response.status())) {

            command.setStatus("REJECTED");
            device.setState("FAILED");
            device.setDeadlineAt(null);
            device.setLastError(
                    response.reason() == null
                            ? "DOWNLOAD rejected"
                            : response.reason());

        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid command response status");
        }

        commandRepository.save(command);
        deviceRepository.save(device);
    }

    private void processTransferComplete(
            String deviceId,
            DeviceInformRequest request) {

        if (request.transferComplete() == null
                || request.transferComplete().commandKey() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing TransferComplete commandKey");
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

        CampaignDevice device = findDevice(
                command.getCampaignId(), deviceId);

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
            // Simplified MVP: successful TransferComplete
            // is treated as successful upgrade completion.
            device.setCurrentVersion(command.getTargetVersion());

        } else {

            command.setStatus("FAILED");
            device.setState("FAILED");
            device.setLastError(
                    "TransferComplete fault: " + event.faultCode());
        }

        device.setDeadlineAt(null);

        commandRepository.save(command);
        deviceRepository.save(device);
    }
}
