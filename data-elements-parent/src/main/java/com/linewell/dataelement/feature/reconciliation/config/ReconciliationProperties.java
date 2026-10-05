package com.linewell.dataelement.feature.reconciliation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "data-element.reconciliation")
public class ReconciliationProperties {

    public enum Role {
        CONTROL,
        WORKER,
        ALL;

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
    private int defaultFetchSize = 2000;
    private int maxFetchSize = 10000;
    private int maxBucketCount = 256;
    private int maxAttempts = 3;
    private int diffWriteBatchSize = 500;
    private Duration connectionTimeout = Duration.ofSeconds(20);
    private Duration workerPollInterval = Duration.ofSeconds(2);
    private Duration schedulePollInterval = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public int getWorkerConcurrency() {
        return workerConcurrency;
    }

    public void setWorkerConcurrency(int workerConcurrency) {
        this.workerConcurrency = workerConcurrency;
    }

    public int getDefaultFetchSize() {
        return defaultFetchSize;
    }

    public void setDefaultFetchSize(int defaultFetchSize) {
        this.defaultFetchSize = defaultFetchSize;
    }

    public int getMaxFetchSize() {
        return maxFetchSize;
    }

    public void setMaxFetchSize(int maxFetchSize) {
        this.maxFetchSize = maxFetchSize;
    }

    public int getMaxBucketCount() {
        return maxBucketCount;
    }

    public void setMaxBucketCount(int maxBucketCount) {
        this.maxBucketCount = maxBucketCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getDiffWriteBatchSize() {
        return diffWriteBatchSize;
    }

    public void setDiffWriteBatchSize(int diffWriteBatchSize) {
        this.diffWriteBatchSize = diffWriteBatchSize;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getWorkerPollInterval() {
        return workerPollInterval;
    }

    public void setWorkerPollInterval(Duration workerPollInterval) {
        this.workerPollInterval = workerPollInterval;
    }

    public Duration getSchedulePollInterval() {
        return schedulePollInterval;
    }

    public void setSchedulePollInterval(Duration schedulePollInterval) {
        this.schedulePollInterval = schedulePollInterval;
    }
}
