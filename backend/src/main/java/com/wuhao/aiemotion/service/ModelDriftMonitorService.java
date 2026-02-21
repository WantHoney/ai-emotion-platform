package com.wuhao.aiemotion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ModelDriftMonitorService {

    private static final Logger log = LoggerFactory.getLogger(ModelDriftMonitorService.class);

    private final AdminGovernanceService adminGovernanceService;

    @Value("${governance.drift.monitor-enabled:true}")
    private boolean monitorEnabled;

    @Value("${governance.drift.window-days:7}")
    private int windowDays;

    @Value("${governance.drift.baseline-days:7}")
    private int baselineDays;

    @Value("${governance.drift.medium-threshold:0.15}")
    private double mediumThreshold;

    @Value("${governance.drift.high-threshold:0.25}")
    private double highThreshold;

    @Value("${governance.drift.min-samples:20}")
    private int minSamples;

    public ModelDriftMonitorService(AdminGovernanceService adminGovernanceService) {
        this.adminGovernanceService = adminGovernanceService;
    }

    @Scheduled(fixedDelayString = "${governance.drift.scan-interval-ms:900000}")
    public void scan() {
        if (!monitorEnabled) {
            return;
        }
        try {
            int created = adminGovernanceService.triggerModelDriftWarnings(
                    windowDays,
                    baselineDays,
                    mediumThreshold,
                    highThreshold,
                    minSamples,
                    "scheduler"
            );
            if (created > 0) {
                log.info("model drift scheduled scan created {} warnings", created);
            }
        } catch (Exception e) {
            log.warn("model drift scheduled scan failed", e);
        }
    }
}

