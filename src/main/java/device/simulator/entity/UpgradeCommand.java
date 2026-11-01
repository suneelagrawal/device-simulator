
package device.simulator.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "upgrade_commands")
public class UpgradeCommand {

    @Id
    private String commandKey;

    @Column(nullable = false)
    private String campaignId;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private String targetVersion;

    private String status = "SENT";

    private Integer faultCode;

    private Instant sentAt = Instant.now();

    private Instant completedAt;

    public UpgradeCommand() {
    }

    public String getCommandKey() {
        return commandKey;
    }

    public void setCommandKey(String commandKey) {
        this.commandKey = commandKey;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getFaultCode() {
        return faultCode;
    }

    public void setFaultCode(Integer faultCode) {
        this.faultCode = faultCode;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
