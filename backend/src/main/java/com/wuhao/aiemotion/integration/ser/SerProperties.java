package com.wuhao.aiemotion.integration.ser;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ser")
public class SerProperties {
    private boolean enabled = false;
    private String baseUrl = "http://localhost:8001";
    private int segmentMs = 8000;
    private int overlapMs = 0;
    private boolean llmSummaryEnabled = false;
    private long connectTimeoutMs = 5000;
    private long readTimeoutMs = 180000;
    private long healthTimeoutMs = 1500;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getSegmentMs() { return segmentMs; }
    public void setSegmentMs(int segmentMs) { this.segmentMs = segmentMs; }
    public int getOverlapMs() { return overlapMs; }
    public void setOverlapMs(int overlapMs) { this.overlapMs = overlapMs; }
    public boolean isLlmSummaryEnabled() { return llmSummaryEnabled; }
    public void setLlmSummaryEnabled(boolean llmSummaryEnabled) { this.llmSummaryEnabled = llmSummaryEnabled; }
    public long getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(long connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public long getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(long readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public long getHealthTimeoutMs() { return healthTimeoutMs; }
    public void setHealthTimeoutMs(long healthTimeoutMs) { this.healthTimeoutMs = healthTimeoutMs; }
}
