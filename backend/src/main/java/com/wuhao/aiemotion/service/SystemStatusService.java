package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.integration.ser.SerClient;
import com.wuhao.aiemotion.integration.ser.SerProperties;
import com.wuhao.aiemotion.repository.AnalysisTaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class SystemStatusService {

    private final JdbcTemplate jdbcTemplate;
    private final SerClient serClient;
    private final SerProperties serProperties;
    private final AnalysisTaskRepository analysisTaskRepository;

    public SystemStatusService(JdbcTemplate jdbcTemplate,
                               SerClient serClient,
                               SerProperties serProperties,
                               AnalysisTaskRepository analysisTaskRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.serClient = serClient;
        this.serProperties = serProperties;
        this.analysisTaskRepository = analysisTaskRepository;
    }

    public Map<String, Object> status() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("backend", serviceStatus("UP", 0, null));
        payload.put("db", probeDb());
        payload.put("ser", probeSer());
        payload.put("metrics", collectMetrics());
        payload.put("config", Map.of(
                "serBaseUrl", serProperties.getBaseUrl(),
                "requestTimeoutMs", serProperties.getReadTimeoutMs()
        ));
        return payload;
    }

    private Map<String, Object> probeDb() {
        long startNs = System.nanoTime();
        try {
            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
            boolean ok = one != null && one == 1;
            return serviceStatus(ok ? "UP" : "DOWN", elapsedMs(startNs), ok ? null : "select 1 returned unexpected value");
        } catch (Exception e) {
            return serviceStatus("DOWN", elapsedMs(startNs), safeMessage(e));
        }
    }

    private Map<String, Object> probeSer() {
        if (!serProperties.isEnabled()) {
            return serviceStatus("DEGRADED", 0, "SER is disabled by configuration");
        }
        long startNs = System.nanoTime();
        try {
            boolean ok = serClient.probeHealth();
            return serviceStatus(ok ? "UP" : "DOWN", elapsedMs(startNs), ok ? null : "SER health probe failed");
        } catch (Exception e) {
            return serviceStatus("DOWN", elapsedMs(startNs), safeMessage(e));
        }
    }

    private Map<String, Object> collectMetrics() {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(24);
            long runningTasks = analysisTaskRepository.countActiveTasks();
            long queuedTasks = analysisTaskRepository.countTasks("PENDING") + analysisTaskRepository.countTasks("RETRY_WAIT");
            long failedTasks24h = analysisTaskRepository.countInStatusSince("FAILED", since);
            Double avgSerLatencyValue = analysisTaskRepository.avgSerLatencySince(since);
            double avgSerLatency = avgSerLatencyValue == null ? 0D : avgSerLatencyValue;
            return Map.of(
                    "runningTasks", runningTasks,
                    "queuedTasks", queuedTasks,
                    "failedTasks24h", failedTasks24h,
                    "avgSerLatencyMs", Math.round(avgSerLatency)
            );
        } catch (Exception e) {
            return Map.of(
                    "runningTasks", 0,
                    "queuedTasks", 0,
                    "failedTasks24h", 0,
                    "avgSerLatencyMs", 0
            );
        }
    }

    private Map<String, Object> serviceStatus(String status, long latencyMs, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status);
        payload.put("latencyMs", latencyMs);
        if (message != null && !message.isBlank()) {
            payload.put("message", message);
        }
        return payload;
    }

    private long elapsedMs(long startNs) {
        return Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
    }

    private String safeMessage(Exception e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getMessage();
    }
}
