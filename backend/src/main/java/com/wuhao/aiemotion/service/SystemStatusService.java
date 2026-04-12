package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.integration.ser.SerClient;
import com.wuhao.aiemotion.integration.ser.SerProperties;
import com.wuhao.aiemotion.repository.AnalysisTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemStatusService {

    private final JdbcTemplate jdbcTemplate;
    private final SerClient serClient;
    private final SerProperties serProperties;
    private final AnalysisTaskRepository analysisTaskRepository;
    private final AnalysisTextScoringProperties textScoringProperties;
    private final AnalysisNarrativeProperties narrativeProperties;
    private final Environment environment;
    private final String aiMode;
    private final String runtimeRegistryEnv;

    public SystemStatusService(JdbcTemplate jdbcTemplate,
                               SerClient serClient,
                               SerProperties serProperties,
                               AnalysisTaskRepository analysisTaskRepository,
                               AnalysisTextScoringProperties textScoringProperties,
                               AnalysisNarrativeProperties narrativeProperties,
                               Environment environment,
                               @Value("${ai.mode:mock}") String aiMode,
                               @Value("${MODEL_RUNTIME_ENV:prod}") String runtimeRegistryEnv) {
        this.jdbcTemplate = jdbcTemplate;
        this.serClient = serClient;
        this.serProperties = serProperties;
        this.analysisTaskRepository = analysisTaskRepository;
        this.textScoringProperties = textScoringProperties;
        this.narrativeProperties = narrativeProperties;
        this.environment = environment;
        this.aiMode = aiMode;
        this.runtimeRegistryEnv = runtimeRegistryEnv;
    }

    public Map<String, Object> status() {
        Map<String, Object> serHealthDetails = serClient.fetchHealthDetails();
        Map<String, Object> payload = new HashMap<>();
        payload.put("backend", serviceStatus("UP", 0, null));
        payload.put("db", probeDb());
        payload.put("ser", probeSer());
        payload.put("metrics", collectMetrics());
        payload.put("runtime", collectRuntime(serHealthDetails));
        payload.put("config", Map.of(
                "serBaseUrl", serProperties.getBaseUrl(),
                "requestTimeoutMs", serProperties.getReadTimeoutMs(),
                "runtimeRegistryEnvHint", normalizeRegistryEnv(runtimeRegistryEnv)
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

    private Map<String, Object> collectRuntime(Map<String, Object> serHealthDetails) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("registryEnvHint", normalizeRegistryEnv(runtimeRegistryEnv));
        runtime.put("activeProfiles", List.of(environment.getActiveProfiles()));
        runtime.put("aiMode", aiMode == null ? "mock" : aiMode.trim());
        runtime.put("modeDescription", describeMode(aiMode));
        runtime.put("textScoringProvider", safeLower(textScoringProperties.getProvider(), "unknown"));
        runtime.put("textScoringFallbackToSer", textScoringProperties.isFallbackToSer());
        runtime.put("narrativeProvider", safeLower(narrativeProperties.getProvider(), "unknown"));
        runtime.put("models", buildRuntimeModels(serHealthDetails));
        return runtime;
    }

    private Map<String, Object> buildRuntimeModels(Map<String, Object> serHealthDetails) {
        Map<String, Object> models = new LinkedHashMap<>();
        models.put("asr", buildAsrModel(serHealthDetails));
        models.put("audioEmotion", buildAudioEmotionModel(serHealthDetails));
        models.put("text", buildTextModel(serHealthDetails));
        models.put("fusion", buildFusionModel(serHealthDetails));
        models.put("psi", buildPsiModel());
        return models;
    }

    private Map<String, Object> buildAsrModel(Map<String, Object> serHealthDetails) {
        String rawValue = firstString(serHealthDetails,
                "asrModel",
                "asr_model",
                "whisperModel",
                "whisper_model",
                "asrWhisperModel");
        String label = rawValue != null ? rawValue : "由 ser-service 托管（未回传型号）";
        return runtimeModel(
                "ASR",
                label,
                rawValue,
                "ser-service",
                rawValue,
                serHealthDetails.isEmpty() ? "UNKNOWN" : "UP",
                rawValue == null ? "健康接口未返回 ASR 型号，仅确认由 ser-service 提供" : null
        );
    }

    private Map<String, Object> buildAudioEmotionModel(Map<String, Object> serHealthDetails) {
        String rawValue = firstString(serHealthDetails,
                "serHfModelDirZh",
                "audioEmotionModelZh",
                "serModelZh");
        String label = simplifyModelPath(rawValue, "本地语音情绪模型（未回传路径）");
        return runtimeModel(
                "AUDIO_EMOTION",
                label,
                rawValue,
                "ser-service",
                simplifyModelPath(rawValue, rawValue),
                serHealthDetails.isEmpty() ? "UNKNOWN" : "UP",
                firstString(serHealthDetails, "serEngine", "engine")
        );
    }

    private Map<String, Object> buildTextModel(Map<String, Object> serHealthDetails) {
        String provider = safeLower(textScoringProperties.getProvider(), "unknown");
        if ("ollama".equals(provider)) {
            String model = narrativeProperties.getOllama().getModel();
            return runtimeModel(
                    "TEXT_SENTIMENT",
                    model + " (Gemma 文本语义)",
                    model,
                    "backend.text-scoring",
                    model,
                    "UP",
                    "provider=ollama"
            );
        }

        String rawValue = firstString(serHealthDetails, "textHfModelZh", "textModelZh");
        return runtimeModel(
                "TEXT_SENTIMENT",
                simplifyModelPath(rawValue, "文本情绪模型（未回传路径）"),
                rawValue,
                "ser-service",
                simplifyModelPath(rawValue, rawValue),
                serHealthDetails.isEmpty() ? "UNKNOWN" : "UP",
                "provider=" + provider
        );
    }

    private Map<String, Object> buildFusionModel(Map<String, Object> serHealthDetails) {
        String rawValue = firstString(serHealthDetails, "fusionZhMode", "fusionMode", "fusion_mode");
        String label = rawValue != null ? rawValue : "融合策略（未回传模式）";
        return runtimeModel(
                "FUSION",
                label,
                rawValue,
                "ser-service",
                rawValue,
                serHealthDetails.isEmpty() ? "UNKNOWN" : "UP",
                null
        );
    }

    private Map<String, Object> buildPsiModel() {
        return runtimeModel(
                "SCORING",
                PsychologicalRiskScoringService.PSI_VERSION,
                PsychologicalRiskScoringService.PSI_VERSION,
                "backend.psi",
                PsychologicalRiskScoringService.PSI_VERSION,
                "UP",
                "启发式风险提示指数（轻量校准版）"
        );
    }

    private Map<String, Object> runtimeModel(String modelType,
                                             String label,
                                             String rawValue,
                                             String source,
                                             String registryComparable,
                                             String status,
                                             String detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modelType", modelType);
        payload.put("label", label == null || label.isBlank() ? "-" : label);
        payload.put("source", source);
        payload.put("status", status);
        if (rawValue != null && !rawValue.isBlank()) {
            payload.put("rawValue", rawValue);
        }
        if (registryComparable != null && !registryComparable.isBlank()) {
            payload.put("registryComparable", registryComparable);
        }
        if (detail != null && !detail.isBlank()) {
            payload.put("detail", detail);
        }
        return payload;
    }

    private String normalizeRegistryEnv(String value) {
        if (value == null || value.isBlank()) {
            return "prod";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "dev", "staging", "prod" -> normalized;
            default -> "prod";
        };
    }

    private String describeMode(String mode) {
        String normalized = safeLower(mode, "mock");
        return switch (normalized) {
            case "mock" -> "当前以后端本地安全兜底为主，适合开发联调与答辩演示。";
            case "spring" -> "当前使用真实推理链路，结果由在线服务和本地模型共同产出。";
            default -> "当前运行模式已启用，请结合系统状态和模型摘要一起判断。";
        };
    }

    private String firstString(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    private String simplifyModelPath(String rawValue, String fallback) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        String normalized = rawValue.replace('\\', '/');
        String[] segments = normalized.split("/");
        if (segments.length >= 2 && "best_model".equalsIgnoreCase(segments[segments.length - 1])) {
            return segments[segments.length - 2];
        }
        return segments[segments.length - 1];
    }

    private String safeLower(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase();
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
