package com.linewell.dataelement.feature.dataquality.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "data-element.data-quality")
public class DataQualityProperties {

    public enum Role {
        CONTROL, WORKER, ALL;

        public boolean controlEnabled() {
            return this == CONTROL || this == ALL;
        }

        public boolean workerEnabled() {
            return this == WORKER || this == ALL;
        }
    }

    private boolean enabled = true;
    private Role role = Role.ALL;
    private int workerConcurrency = 2;
    private int maxShardCount = 128;
    private int maxAttempts = 3;
    private int maxSampleLimit = 2000;
    private int connectionTimeoutSeconds = 20;
    private Duration workerPollInterval = Duration.ofSeconds(3);
    private Duration schedulePollInterval = Duration.ofSeconds(30);
    private Duration staleLease = Duration.ofMinutes(15);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public int getWorkerConcurrency() { return workerConcurrency; }
    public void setWorkerConcurrency(int value) { this.workerConcurrency = value; }
    public int getMaxShardCount() { return maxShardCount; }
    public void setMaxShardCount(int value) { this.maxShardCount = value; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { this.maxAttempts = value; }
    public int getMaxSampleLimit() { return maxSampleLimit; }
    public void setMaxSampleLimit(int value) { this.maxSampleLimit = value; }
    public int getConnectionTimeoutSeconds() { return connectionTimeoutSeconds; }
    public void setConnectionTimeoutSeconds(int value) { this.connectionTimeoutSeconds = value; }
    public Duration getWorkerPollInterval() { return workerPollInterval; }
    public void setWorkerPollInterval(Duration value) { this.workerPollInterval = value; }
    public Duration getSchedulePollInterval() { return schedulePollInterval; }
    public void setSchedulePollInterval(Duration value) { this.schedulePollInterval = value; }
    public Duration getStaleLease() { return staleLease; }
    public void setStaleLease(Duration value) { this.staleLease = value; }
}

