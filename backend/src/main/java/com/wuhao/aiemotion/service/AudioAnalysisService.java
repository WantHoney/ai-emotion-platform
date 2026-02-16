package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.domain.AudioAnalysis;
import com.wuhao.aiemotion.dto.EmotionAnalysisReport;
import com.wuhao.aiemotion.dto.response.*;
import com.wuhao.aiemotion.integration.ai.AiAnalysisRequest;
import com.wuhao.aiemotion.integration.ai.AiAnalysisResult;
import com.wuhao.aiemotion.integration.ai.AiClient;
import com.wuhao.aiemotion.integration.ai.AiProperties;
import com.wuhao.aiemotion.integration.ser.SerAnalyzeResponse;
import com.wuhao.aiemotion.integration.ser.SerClient;
import com.wuhao.aiemotion.integration.ser.SerProperties;
import com.wuhao.aiemotion.repository.AudioAnalysisRepository;
import com.wuhao.aiemotion.repository.AudioRepository;
import com.wuhao.aiemotion.repository.CoreReportRepository;
import com.wuhao.aiemotion.repository.ReportMockRepository;
import com.wuhao.aiemotion.repository.SegmentEmotionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class AudioAnalysisService {

    private final AudioAnalysisRepository analysisRepository;
    private final AudioRepository audioRepository;
    private final SegmentEmotionRepository segmentEmotionRepository;
    private final ReportMockRepository reportMockRepository;
    private final CoreReportRepository coreReportRepository;
    private final AiClient aiClient;
    private final AiProperties aiProperties;
    private final SerClient serClient;
    private final SerProperties serProperties;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int TOP_N_EMOTIONS_PER_SEGMENT = 2;

    private static final Set<String> ALLOWED_STATUS =
            Set.of("PENDING", "RUNNING", "SUCCESS", "FAILED");

    public AudioAnalysisService(AudioAnalysisRepository analysisRepository,
                                AudioRepository audioRepository,
                                SegmentEmotionRepository segmentEmotionRepository,
                                ReportMockRepository reportMockRepository,
                                CoreReportRepository coreReportRepository,
                                AiClient aiClient,
                                AiProperties aiProperties,
                                SerClient serClient,
                                SerProperties serProperties,
                                ObjectMapper objectMapper) {
        this.analysisRepository = analysisRepository;
        this.audioRepository = audioRepository;
        this.segmentEmotionRepository = segmentEmotionRepository;
        this.reportMockRepository = reportMockRepository;
        this.coreReportRepository = coreReportRepository;
        this.aiClient = aiClient;
        this.aiProperties = aiProperties;
        this.serClient = serClient;
        this.serProperties = serProperties;
        this.objectMapper = objectMapper;
    }

    public AudioAnalysisStartResponse start(long audioId, String modelName, String modelVersion) {
        if (!analysisRepository.audioExists(audioId)) {
            throw new IllegalArgumentException("audio_id 不存在: " + audioId);
        }
        if (modelName == null || modelName.isBlank()) modelName = "default";

        long id = analysisRepository.insert(audioId, modelName, modelVersion);
        return new AudioAnalysisStartResponse(id, audioId, "PENDING");
    }

    public AudioAnalysisDetailResponse detail(long analysisId) {
        AudioAnalysis a = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("analysis 不存在: " + analysisId));
        return toDetailResponse(a);
    }

    public AudioAnalysisDetailResponse latestByAudio(long audioId) {
        AudioAnalysis a = analysisRepository.findLatestByAudioId(audioId)
                .orElseThrow(() -> new IllegalArgumentException("该音频暂无分析记录: " + audioId));
        return toDetailResponse(a);
    }

    public AudioAnalysisListResponse listByAudio(long audioId, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;

        int offset = (page - 1) * size;

        long total = analysisRepository.countByAudioId(audioId);
        List<AudioAnalysis> list = analysisRepository.findPageByAudioId(audioId, offset, size);

        List<AudioAnalysisListResponse.Item> items = list.stream()
                .map(a -> new AudioAnalysisListResponse.Item(
                        a.id(),
                        a.audioId(),
                        a.modelName(),
                        a.modelVersion(),
                        a.status(),
                        a.createdAt() == null ? null : a.createdAt().format(FMT)
                ))
                .toList();

        return new AudioAnalysisListResponse(total, page, size, items);
    }

    /**
     * ✅ B1：全局管理列表（status 校验 + 带音频原始名）
     */
    public AudioAnalysisAdminListResponse adminList(int page, int size, String status) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;

        String normalizedStatus = normalizeStatus(status);

        int offset = (page - 1) * size;

        long total = analysisRepository.countAll(normalizedStatus);
        var rawItems = analysisRepository.findAdminPage(offset, size, normalizedStatus);

        // 统一时间格式（Repository 的 toString 可能是 2026-02-07T10:12:33）
        var items = rawItems.stream().map(it ->
                new AudioAnalysisAdminListResponse.Item(
                        it.id(),
                        it.audioId(),
                        it.audioOriginalName(),
                        it.modelName(),
                        it.modelVersion(),
                        it.status(),
                        normalizeDateTimeString(it.createdAt()),
                        normalizeDateTimeString(it.updatedAt())
                )
        ).toList();

        return new AudioAnalysisAdminListResponse(total, page, size, items);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUS.contains(s)) {
            throw new IllegalArgumentException("status 非法，仅支持: " + ALLOWED_STATUS + "，当前: " + status);
        }
        return s;
    }

    private String normalizeDateTimeString(String s) {
        if (s == null || s.isBlank()) return null;
        // 兼容 "2026-02-07T10:12:33" / "2026-02-07 10:12:33"
        try {
            if (s.contains("T")) {
                LocalDateTime dt = LocalDateTime.parse(s);
                return dt.format(FMT);
            }
        } catch (Exception ignore) {}
        return s;
    }

    /**
     * ✅ report：优先读 core_report（快照），没有才实时计算
     * （B3：你这里已经是正确实现，不需要再改）
     */
    public AudioAnalysisReportResponse report(long analysisId) {
        Optional<String> jsonOpt = coreReportRepository.findLatestReportJsonByAnalysisId(analysisId);
        if (jsonOpt.isPresent()) {
            try {
                return objectMapper.readValue(jsonOpt.get(), AudioAnalysisReportResponse.class);
            } catch (Exception ignore) {
                try {
                    EmotionAnalysisReport llmReport = objectMapper.readValue(jsonOpt.get(), EmotionAnalysisReport.class);
                    return buildReportFromLlm(analysisId, llmReport);
                } catch (Exception ignore2) {
                    // 解析失败就走实时计算兜底
                }
            }
        }
        return buildReport(analysisId);
    }

    public AudioAnalysisReportResponse generateAndSaveReport(long analysisId) {
        AudioAnalysisReportResponse report = buildReport(analysisId);
        try {
            String json = objectMapper.writeValueAsString(report);
            coreReportRepository.upsert(analysisId, "Emotion Report", json);
        } catch (Exception e) {
            throw new IllegalStateException("生成报告JSON失败: " + e.getMessage(), e);
        }
        return report;
    }

    public void mockSuccess(long analysisId) {
        String mockJson = """
                {"overallEmotion":"HAPPY","confidence":0.87,"note":"mock result (no AI yet)"}
                """;
        int n = analysisRepository.updateStatus(analysisId, "SUCCESS", mockJson, null);
        if (n == 0) throw new IllegalArgumentException("analysis 不存在: " + analysisId);
    }

    public void mockFail(long analysisId, String errorMessage) {
        markFailed(analysisId, errorMessage == null || errorMessage.isBlank() ? "mock failed" : errorMessage);
    }

    private void markRunning(long analysisId) {
        int n = analysisRepository.updateStatus(analysisId, "RUNNING", null, null);
        if (n == 0) throw new IllegalArgumentException("analysis 不存在: " + analysisId);
    }

    private void markFailed(long analysisId, String errorMessage) {
        int n = analysisRepository.updateStatus(analysisId, "FAILED", null, errorMessage);
        if (n == 0) throw new IllegalArgumentException("analysis 不存在: " + analysisId);
    }

    public void mockSegments(long analysisId) {
        analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("analysis 不存在: " + analysisId));

        AiAnalysisResult result = aiClient.analyze(buildAiRequest(analysisId));
        persistSegmentsAndEmotions(analysisId, result);
    }

    // ---------------- helpers ----------------

    private AiAnalysisRequest buildAiRequest(long analysisId) {
        AudioAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("analysis 不存在: " + analysisId));

        AiAnalysisRequest request = AiAnalysisRequest.fromAudioId(analysisId, analysis.audioId(), analysis.modelName())
                .withOptions(aiProperties.getLanguage(), aiProperties.getSampleRate(), analysis.modelName());

        return audioRepository.findById(analysis.audioId())
                .map(request::withAudioFile)
                .orElse(request);
    }

    private void applySuccessResult(long analysisId, AiAnalysisResult result) {
        String summaryJson = serializeJson(result.summaryJson());
        int n = analysisRepository.updateStatus(analysisId, "SUCCESS", summaryJson, null);
        if (n == 0) throw new IllegalArgumentException("analysis 不存在: " + analysisId);
    }

    private void persistSegmentsAndEmotions(long analysisId, AiAnalysisResult result) {
        if (reportMockRepository.hasSegments(analysisId)) {
            return;
        }
        for (AiAnalysisResult.AiSegment segment : result.segments()) {
            long segmentId = reportMockRepository.insertSegment(
                    analysisId,
                    segment.startMs(),
                    segment.endMs(),
                    segment.text()
            );
            for (AiAnalysisResult.AiEmotionScore emotion : segment.emotions()) {
                long emotionId = reportMockRepository.ensureEmotionLabel(
                        emotion.code(),
                        emotion.nameZh(),
                        emotion.scheme()
                );
                reportMockRepository.upsertSegmentEmotion(segmentId, emotionId, emotion.score());
            }
        }
    }

    private void upsertReport(long analysisId, AiAnalysisResult result) {
        if (result.reportJson() == null) {
            generateAndSaveReport(analysisId);
            return;
        }
        String reportJson = serializeJson(result.reportJson());
        coreReportRepository.upsert(analysisId, "Emotion Report", reportJson);
    }

    private String serializeJson(Object payload) {
        if (payload == null) return null;
        if (payload instanceof String text) return text;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return payload.toString();
        }
    }

    private AudioAnalysisReportResponse buildReport(long analysisId) {
        AudioAnalysis a = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("analysis 不存在: " + analysisId));

        Object summaryObj = parseSummaryJson(a.summaryJson());

        List<Map<String, Object>> segRows = segmentEmotionRepository.findSegmentsWithEmotions(analysisId);

        Map<String, Double> scoreSumByCode = new HashMap<>();
        Map<String, String> nameZhByCode = new HashMap<>();

        List<AudioAnalysisReportResponse.Segment> segments = segRows.stream().map(seg -> {
            long segmentId = ((Number) seg.get("id")).longValue();
            long startMs = ((Number) seg.get("start_ms")).longValue();
            long endMs = ((Number) seg.get("end_ms")).longValue();
            String transcript = (String) seg.get("transcript");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> emoRows = (List<Map<String, Object>>) seg.get("emotions");
            if (emoRows == null) emoRows = List.of();

            List<Map<String, Object>> topRows = emoRows.size() <= TOP_N_EMOTIONS_PER_SEGMENT
                    ? emoRows
                    : emoRows.subList(0, TOP_N_EMOTIONS_PER_SEGMENT);

            List<AudioAnalysisReportResponse.Emotion> emotions = topRows.stream().map(er -> {
                String code = (String) er.get("code");
                String nameZh = (String) er.get("name_zh");
                double score = ((Number) er.get("score")).doubleValue();

                scoreSumByCode.merge(code, score, Double::sum);
                nameZhByCode.putIfAbsent(code, nameZh);

                return new AudioAnalysisReportResponse.Emotion(
                        ((Number) er.get("emotion_id")).longValue(),
                        code,
                        nameZh,
                        (String) er.get("scheme"),
                        score
                );
            }).toList();

            return new AudioAnalysisReportResponse.Segment(
                    segmentId, startMs, endMs, transcript, emotions
            );
        }).toList();

        AudioAnalysisReportResponse.Overall overall = calcOverall(scoreSumByCode, nameZhByCode);

        return new AudioAnalysisReportResponse(
                a.id(),
                a.audioId(),
                a.modelName(),
                a.modelVersion(),
                a.status(),
                summaryObj,
                a.errorMessage(),
                a.createdAt() == null ? null : a.createdAt().format(FMT),
                a.updatedAt() == null ? null : a.updatedAt().format(FMT),
                overall,
                segments
        );
    }

    private AudioAnalysisReportResponse buildReportFromLlm(long analysisId, EmotionAnalysisReport report) {
        AudioAnalysis a = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("analysis 不存在: " + analysisId));

        return new AudioAnalysisReportResponse(
                a.id(),
                a.audioId(),
                a.modelName(),
                a.modelVersion(),
                a.status(),
                report,
                a.errorMessage(),
                a.createdAt() == null ? null : a.createdAt().format(FMT),
                a.updatedAt() == null ? null : a.updatedAt().format(FMT),
                null,
                List.of()
        );
    }

    private AudioAnalysisDetailResponse toDetailResponse(AudioAnalysis a) {
        Object summaryObj = parseSummaryJson(a.summaryJson());
        return new AudioAnalysisDetailResponse(
                a.id(),
                a.audioId(),
                a.modelName(),
                a.modelVersion(),
                a.status(),
                summaryObj,
                a.errorMessage(),
                a.createdAt() == null ? null : a.createdAt().format(FMT),
                a.updatedAt() == null ? null : a.updatedAt().format(FMT)
        );
    }

    private Object parseSummaryJson(String summaryJson) {
        if (summaryJson == null || summaryJson.isBlank()) return null;
        try {
            return objectMapper.readValue(summaryJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignore) {
            try {
                return objectMapper.readValue(summaryJson, Object.class);
            } catch (Exception ignore2) {
                return summaryJson;
            }
        }
    }

    private AudioAnalysisReportResponse.Overall calcOverall(Map<String, Double> scoreSumByCode,
                                                            Map<String, String> nameZhByCode) {
        if (scoreSumByCode.isEmpty()) return null;

        double total = 0.0;
        for (double v : scoreSumByCode.values()) total += v;

        String bestCode = null;
        double bestScore = -1;
        for (var e : scoreSumByCode.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                bestCode = e.getKey();
            }
        }
        if (bestCode == null) return null;

        double confidence = total <= 0 ? 0.0 : bestScore / total;
        String nameZh = nameZhByCode.getOrDefault(bestCode, bestCode);

        return new AudioAnalysisReportResponse.Overall(bestCode, nameZh, confidence);
    }

    private void runSerAnalysis(long analysisId, long audioId) {
        var audioFile = audioRepository.findById(audioId)
                .orElseThrow(() -> new IllegalArgumentException("audio 不存在: " + audioId));

        SerAnalyzeResponse ser = serClient.analyze(java.nio.file.Path.of(audioFile.storagePath()));

        reportMockRepository.deleteSegmentsByAnalysisId(analysisId);
        for (SerAnalyzeResponse.Segment segment : ser.segments()) {
            long segmentId = reportMockRepository.insertSegment(
                    analysisId,
                    segment.startMs(),
                    segment.endMs(),
                    null
            );
            String code = segment.emotionCode();
            long emotionId = reportMockRepository.ensureEmotionLabel(
                    code,
                    emotionNameZh(code),
                    "SER_V1"
            );
            reportMockRepository.upsertSegmentEmotion(segmentId, emotionId, segment.confidence());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("source", "ser");
        summary.put("overall", ser.overall());
        summary.put("meta", ser.meta());
        if (serProperties.isLlmSummaryEnabled()) {
            summary.put("narrative", "LLM summary hook enabled. Provide SER JSON to OpenRouter summarizer here.");
        }

        int n = analysisRepository.updateStatus(analysisId, "SUCCESS", serializeJson(summary), null);
        if (n == 0) throw new IllegalArgumentException("analysis 不存在: " + analysisId);
        generateAndSaveReport(analysisId);
    }

    private String emotionNameZh(String code) {
        if (code == null) return "未知";
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "HAPPY" -> "开心";
            case "SAD" -> "难过";
            case "ANGRY" -> "生气";
            case "CALM" -> "平静";
            case "FEAR" -> "恐惧";
            case "NEUTRAL" -> "中性";
            default -> code;
        };
    }

    /**
     * ✅ mock-run（同步）
     */
    public AudioAnalysisReportResponse mockRun(long analysisId) {
        return executeAnalysisSync(analysisId);
    }

    /**
     * ✅ run（同步）：真实执行入口
     */
    public AudioAnalysisReportResponse run(long analysisId) {
        return executeAnalysisSync(analysisId);
    }

    private AudioAnalysisReportResponse executeAnalysisSync(long analysisId) {
        AudioAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("analysis 不存在: " + analysisId));

        markRunning(analysisId);
        try {
            if (serProperties.isEnabled()) {
                runSerAnalysis(analysisId, analysis.audioId());
            } else {
                AiAnalysisResult result = aiClient.analyze(buildAiRequest(analysisId));
                applySuccessResult(analysisId, result);
                persistSegmentsAndEmotions(analysisId, result);
                upsertReport(analysisId, result);
            }
            return report(analysisId);
        } catch (Exception e) {
            markFailed(analysisId, "analysis failed: " + e.getMessage());
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("analysis failed", e);
        }
    }

    /**
     * ✅ B2：异步 mock-run：立即返回 RUNNING，后台完成 success + segments + report
     */
    public AudioAnalysisRunResponse mockRunAsync(long analysisId) {
        return executeAnalysisAsync(analysisId);
    }

    /**
     * ✅ run-async：真实执行入口（异步）
     */
    public AudioAnalysisRunResponse runAsync(long analysisId) {
        return executeAnalysisAsync(analysisId);
    }

    private AudioAnalysisRunResponse executeAnalysisAsync(long analysisId) {
        AudioAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("analysis 不存在: " + analysisId));

        markRunning(analysisId);

        CompletableFuture.runAsync(() -> {
            try {
                if (serProperties.isEnabled()) {
                    runSerAnalysis(analysisId, analysis.audioId());
                } else {
                    AiAnalysisResult result = aiClient.analyze(buildAiRequest(analysisId));
                    applySuccessResult(analysisId, result);
                    persistSegmentsAndEmotions(analysisId, result);
                    upsertReport(analysisId, result);
                }
            } catch (Exception e) {
                markFailed(analysisId, "analysis failed: " + e.getMessage());
            }
        });

        return new AudioAnalysisRunResponse(analysisId, "RUNNING");
    }

    /**
     * ✅ B4：删除分析（物理删除）
     * 依赖你表上的外键 ON DELETE CASCADE（segment/segment_emotion/core_report 都会跟着删）
     */
    public AudioAnalysisDeleteResponse deleteAnalysis(long analysisId) {
        int n = analysisRepository.deleteById(analysisId);
        if (n == 0) throw new IllegalArgumentException("analysis 不存在: " + analysisId);
        return new AudioAnalysisDeleteResponse(analysisId, "DELETED");
    }
}
