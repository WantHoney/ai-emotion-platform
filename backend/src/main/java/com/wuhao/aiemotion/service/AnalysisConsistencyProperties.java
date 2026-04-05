package com.wuhao.aiemotion.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis.consistency")
public class AnalysisConsistencyProperties {

    private boolean enabled = true;
    private boolean auditLogEnabled = true;
    private SadPositiveConflict sadPositiveConflict = new SadPositiveConflict();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAuditLogEnabled() {
        return auditLogEnabled;
    }

    public void setAuditLogEnabled(boolean auditLogEnabled) {
        this.auditLogEnabled = auditLogEnabled;
    }

    public SadPositiveConflict getSadPositiveConflict() {
        return sadPositiveConflict;
    }

    public void setSadPositiveConflict(SadPositiveConflict sadPositiveConflict) {
        this.sadPositiveConflict = sadPositiveConflict;
    }

    public static class SadPositiveConflict {
        private boolean enabled = true;
        private double voiceConfidenceMin = 0.98D;
        private double fusedTextNegMax = 0.45D;
        private int transcriptExcerptMaxChars = 120;
        private int transcriptLeftContextChars = 24;
        private int transcriptRightContextChars = 72;
        private String positiveLexiconResource = "lexicon/consistency_positive_zh.txt";
        private String negativeLexiconResource = "lexicon/consistency_negative_zh.txt";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getVoiceConfidenceMin() {
            return voiceConfidenceMin;
        }

        public void setVoiceConfidenceMin(double voiceConfidenceMin) {
            this.voiceConfidenceMin = voiceConfidenceMin;
        }

        public double getFusedTextNegMax() {
            return fusedTextNegMax;
        }

        public void setFusedTextNegMax(double fusedTextNegMax) {
            this.fusedTextNegMax = fusedTextNegMax;
        }

        public int getTranscriptExcerptMaxChars() {
            return transcriptExcerptMaxChars;
        }

        public void setTranscriptExcerptMaxChars(int transcriptExcerptMaxChars) {
            this.transcriptExcerptMaxChars = transcriptExcerptMaxChars;
        }

        public int getTranscriptLeftContextChars() {
            return transcriptLeftContextChars;
        }

        public void setTranscriptLeftContextChars(int transcriptLeftContextChars) {
            this.transcriptLeftContextChars = transcriptLeftContextChars;
        }

        public int getTranscriptRightContextChars() {
            return transcriptRightContextChars;
        }

        public void setTranscriptRightContextChars(int transcriptRightContextChars) {
            this.transcriptRightContextChars = transcriptRightContextChars;
        }

        public String getPositiveLexiconResource() {
            return positiveLexiconResource;
        }

        public void setPositiveLexiconResource(String positiveLexiconResource) {
            this.positiveLexiconResource = positiveLexiconResource;
        }

        public String getNegativeLexiconResource() {
            return negativeLexiconResource;
        }

        public void setNegativeLexiconResource(String negativeLexiconResource) {
            this.negativeLexiconResource = negativeLexiconResource;
        }
    }
}
