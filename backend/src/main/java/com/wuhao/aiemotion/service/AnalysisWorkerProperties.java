package com.wuhao.aiemotion.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis.worker")
public class AnalysisWorkerProperties {

    private boolean enabled = true;
    private long pollIntervalMs = 1000;
    private int batchSize = 20;
    private int maxAttempts = 4;
    private long serTimeoutMs = 180000;
    private long asrTimeoutMs = 90000;
    private int backoffBaseSeconds = 30;
    private int backoffMaxSeconds = 600;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getSerTimeoutMs() {
        return serTimeoutMs;
    }

    public void setSerTimeoutMs(long serTimeoutMs) {
        this.serTimeoutMs = serTimeoutMs;
    }

    public long getAsrTimeoutMs() {
        return asrTimeoutMs;
    }

    public void setAsrTimeoutMs(long asrTimeoutMs) {
        this.asrTimeoutMs = asrTimeoutMs;
    }

    public int getBackoffBaseSeconds() {
        return backoffBaseSeconds;
    }

    public void setBackoffBaseSeconds(int backoffBaseSeconds) {
        this.backoffBaseSeconds = backoffBaseSeconds;
    }

    public int getBackoffMaxSeconds() {
        return backoffMaxSeconds;
    }

    public void setBackoffMaxSeconds(int backoffMaxSeconds) {
        this.backoffMaxSeconds = backoffMaxSeconds;
    }
}
