
package device.simulator.controller;

import device.simulator.dto.*;
import device.simulator.service.CampaignService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignStatusResponse create(
            @Valid @RequestBody CreateCampaignRequest request) {
        return campaignService.createCampaign(request);
    }

    @GetMapping("/{id}")
    public CampaignStatusResponse status(@PathVariable String id) {
        return campaignService.getCampaignStatus(id);
    }

    @PostMapping("/{id}/pause")
    public CampaignStatusResponse pause(@PathVariable String id) {
        return campaignService.pauseCampaign(id);
    }

    @PostMapping("/{id}/resume")
    public CampaignStatusResponse resume(@PathVariable String id) {
        return campaignService.resumeCampaign(id);
    }
}
