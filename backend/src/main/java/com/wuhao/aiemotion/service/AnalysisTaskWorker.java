package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.domain.AnalysisTask;
import com.wuhao.aiemotion.integration.ser.SerClient;
import com.wuhao.aiemotion.integration.ser.SerProperties;
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
    private static final long SER_PROBE_COOLDOWN_MS = 5000;

    private final AnalysisTaskWorkerService workerService;
    private final AnalysisWorkerProperties workerProperties;
    private final SerClient serClient;
    private final SerProperties serProperties;
    private final String workerId;
    private volatile long lastSerProbeAtMs = 0;
    private volatile boolean lastSerUp = true;
    private volatile boolean serDownLogged = false;

    public AnalysisTaskWorker(AnalysisTaskWorkerService workerService,
                              AnalysisWorkerProperties workerProperties,
                              SerClient serClient,
                              SerProperties serProperties,
                              @Value("${spring.application.name:ai-emotion-backend}") String appName) {
        this.workerService = workerService;
        this.workerProperties = workerProperties;
        this.serClient = serClient;
        this.serProperties = serProperties;
        this.workerId = buildWorkerId(appName);
        log.info("analysis worker started: workerId={}, pollIntervalMs={}, batchSize={}",
                workerId, workerProperties.getPollIntervalMs(), workerProperties.getBatchSize());
    }

    @Scheduled(fixedDelayString = "${analysis.worker.poll-interval-ms:1000}")
    public void pollAndProcess() {
        if (!workerProperties.isEnabled()) {
            return;
        }
        if (!shouldConsumeTasks()) {
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

    private boolean shouldConsumeTasks() {
        if (!serProperties.isEnabled()) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - lastSerProbeAtMs < SER_PROBE_COOLDOWN_MS) {
            return lastSerUp;
        }

        lastSerProbeAtMs = now;
        boolean up = serClient.probeHealth();
        if (!up) {
            if (!serDownLogged) {
                log.warn("SER health probe failed ({}), pause analysis task consumption until recovery",
                        serProperties.getBaseUrl());
                serDownLogged = true;
            }
            lastSerUp = false;
            return false;
        }

        if (serDownLogged) {
            log.info("SER health recovered ({}), resume analysis task consumption", serProperties.getBaseUrl());
            serDownLogged = false;
        }
        lastSerUp = true;
        return true;
    }
}
