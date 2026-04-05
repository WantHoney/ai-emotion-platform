package com.wuhao.aiemotion.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis.narrative")
public class AnalysisNarrativeProperties {

    private boolean enabled = true;
    private String provider = "ollama";
    private String language = "zh-CN";
    private int maxTranscriptChars = 600;
    private int maxSegments = 3;
    private String safetyNotice = "以上内容仅作辅助参考，不构成医疗诊断。若持续感到痛苦或风险升高，请尽快联系值得信任的人或专业支持资源。";
    private Ollama ollama = new Ollama();
    private Trend trend = new Trend();

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

    public int getMaxSegments() {
        return maxSegments;
    }

    public void setMaxSegments(int maxSegments) {
        this.maxSegments = maxSegments;
    }

    public String getSafetyNotice() {
        return safetyNotice;
    }

    public void setSafetyNotice(String safetyNotice) {
        this.safetyNotice = safetyNotice;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }

    public Trend getTrend() {
        return trend;
    }

    public void setTrend(Trend trend) {
        this.trend = trend;
    }

    public static class Ollama {
        private String baseUrl = "http://127.0.0.1:11434";
        private String model = "gemma4:e4b";
        private long connectTimeoutMs = 3000;
        private long readTimeoutMs = 180000;
        private double temperature = 0.35D;
        private boolean preload = true;
        private String keepAlive = "30m";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public long getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public long getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(long readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public boolean isPreload() {
            return preload;
        }

        public void setPreload(boolean preload) {
            this.preload = preload;
        }

        public String getKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(String keepAlive) {
            this.keepAlive = keepAlive;
        }
    }

    public static class Trend {
        private boolean enabled = true;
        private int maxPoints = 12;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxPoints() {
            return maxPoints;
        }

        public void setMaxPoints(int maxPoints) {
            this.maxPoints = maxPoints;
        }
    }
}
