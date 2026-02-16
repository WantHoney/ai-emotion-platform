package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.domain.AnalysisTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

@Component
public class AnalysisTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(AnalysisTaskWorker.class);

    private final AnalysisTaskWorkerService workerService;
    private final AnalysisWorkerProperties workerProperties;
    private final String workerId;

    public AnalysisTaskWorker(AnalysisTaskWorkerService workerService,
                              AnalysisWorkerProperties workerProperties,
                              @Value("${spring.application.name:ai-emotion-backend}") String appName) {
        this.workerService = workerService;
        this.workerProperties = workerProperties;
        this.workerId = buildWorkerId(appName);
        log.info("analysis worker started: workerId={}, pollIntervalMs={}, batchSize={}",
                workerId, workerProperties.getPollIntervalMs(), workerProperties.getBatchSize());
    }

    @Scheduled(fixedDelayString = "${analysis.worker.poll-interval-ms:1000}")
    public void pollAndProcess() {
        if (!workerProperties.isEnabled()) {
            return;
        }
        List<AnalysisTask> candidates = workerService.findCandidates();
        for (AnalysisTask task : candidates) {
            int updated = workerService.claim(task.id(), workerId);
            if (updated == 1) {
                workerService.processClaimedTask(task, workerId);
            }
        }
    }

    private String buildWorkerId(String appName) {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            return appName + "-" + host + "-" + pid;
        } catch (Exception e) {
            return appName + "-" + UUID.randomUUID();
        }
    }
}
