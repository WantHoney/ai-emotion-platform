package com.wuhao.aiemotion.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis.text-scoring")
public class AnalysisTextScoringProperties {

    private boolean enabled = true;
    private String provider = "ollama";
    private String language = "zh";
    private int maxTranscriptChars = 240;
    private boolean fallbackToSer = true;
    private double temperature = 0.10D;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getMaxTranscriptChars() {
        return maxTranscriptChars;
    }

    public void setMaxTranscriptChars(int maxTranscriptChars) {
        this.maxTranscriptChars = maxTranscriptChars;
    }

    public boolean isFallbackToSer() {
        return fallbackToSer;
    }

    public void setFallbackToSer(boolean fallbackToSer) {
        this.fallbackToSer = fallbackToSer;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}
