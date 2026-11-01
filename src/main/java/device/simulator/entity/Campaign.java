
package device.simulator.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String targetVersion;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private String sourceVersion;

    private int maxConcurrent;

    private double failureThresholdPercent;

    @Column(nullable = false)
    private String status = "CREATED";

    private Instant createdAt = Instant.now();

    public Campaign() {
    }

    public String getId() {
        return id;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public double getFailureThresholdPercent() {
        return failureThresholdPercent;
    }

    public void setFailureThresholdPercent(double failureThresholdPercent) {
        this.failureThresholdPercent = failureThresholdPercent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
