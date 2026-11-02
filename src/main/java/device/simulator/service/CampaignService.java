package device.simulator.service;

import device.simulator.dto.*;
import device.simulator.entity.*;
import device.simulator.repository.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignDeviceRepository deviceRepository;
    private final RestClient simulatorClient;

    public CampaignService(
            CampaignRepository campaignRepository,
            CampaignDeviceRepository deviceRepository,
            @Value("${simulator.base-url}") String simulatorUrl) {
        this.campaignRepository = campaignRepository;
        this.deviceRepository = deviceRepository;
        this.simulatorClient = RestClient.create(simulatorUrl);
    }

    public record SimulatorDevice(
            String deviceId,
            String firmwareVersion
    ) {}

    @Transactional
    public CampaignStatusResponse createCampaign(CreateCampaignRequest request) {

        SimulatorDevice[] devices = simulatorClient.get()
                .uri(builder -> builder.path("/devices")
                        .queryParam("version", request.deviceFilter().currentVersion())
                        .build())
                .retrieve()
                .body(SimulatorDevice[].class);

        if (devices == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Simulator inventory unavailable");
        }

        Campaign campaign = new Campaign();
        campaign.setTargetVersion(request.targetVersion());
        campaign.setImageUrl(request.imageUrl());
        campaign.setSourceVersion(request.deviceFilter().currentVersion());
        campaign.setMaxConcurrent(request.maxConcurrent());
        campaign.setFailureThresholdPercent(request.failureThresholdPercent());
        campaign.setStatus("RUNNING");

        campaign = campaignRepository.save(campaign);

        List<CampaignDevice> campaignDevices = new ArrayList<>();

        for (SimulatorDevice device : devices) {
            CampaignDevice cd = new CampaignDevice();
            cd.setCampaignId(campaign.getId());
            cd.setDeviceId(device.deviceId());
            cd.setCurrentVersion(device.firmwareVersion());
            cd.setState("PENDING");
            campaignDevices.add(cd);
        }

        deviceRepository.saveAll(campaignDevices);
        return getCampaignStatus(campaign.getId());
    }

    @Transactional(readOnly = true)
    public CampaignStatusResponse getCampaignStatus(String id) {
        Campaign campaign = findCampaign(id);
        List<CampaignDevice> devices = deviceRepository.findByCampaignId(id);

        Map<String, Long> counts = devices.stream()
                .collect(Collectors.groupingBy(
                        CampaignDevice::getState, Collectors.counting()));

        long succeeded = counts.getOrDefault("SUCCEEDED", 0L);
        long failed = counts.getOrDefault("FAILED", 0L);
        long timedOut = counts.getOrDefault("TIMED_OUT", 0L);
        long terminal = succeeded + failed + timedOut;

        double failureRate = terminal == 0 ? 0.0
                : (failed + timedOut) * 100.0 / terminal;

        return new CampaignStatusResponse(
                campaign.getId(),
                campaign.getStatus(),
                campaign.getTargetVersion(),
                devices.size(),
                counts,
                failureRate);
    }

    @Transactional
    public CampaignStatusResponse pauseCampaign(String id) {
        Campaign campaign = findCampaign(id);

        if (!"RUNNING".equals(campaign.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Campaign is not running");
        }

        campaign.setStatus("PAUSED");
        return getCampaignStatus(id);
    }

    @Transactional
    public CampaignStatusResponse resumeCampaign(String id) {
        Campaign campaign = findCampaign(id);

        if (!"PAUSED".equals(campaign.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Campaign is not paused");
        }

        campaign.setStatus("RUNNING");
        return getCampaignStatus(id);
    }

    private Campaign findCampaign(String id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Campaign not found"));
    }
}
