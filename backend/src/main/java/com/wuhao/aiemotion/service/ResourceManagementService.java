package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.domain.AnalysisTask;
import com.wuhao.aiemotion.domain.AudioFile;
import com.wuhao.aiemotion.domain.ReportResource;
import com.wuhao.aiemotion.dto.response.AdminMetricsResponse;
import com.wuhao.aiemotion.dto.response.AudioListResponse;
import com.wuhao.aiemotion.dto.response.ReportListResponse;
import com.wuhao.aiemotion.dto.response.TaskListResponse;
import com.wuhao.aiemotion.repository.AnalysisResultRepository;
import com.wuhao.aiemotion.repository.AnalysisSegmentRepository;
import com.wuhao.aiemotion.repository.AnalysisTaskRepository;
import com.wuhao.aiemotion.repository.AudioRepository;
import com.wuhao.aiemotion.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResourceManagementService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(ResourceManagementService.class);

    private final AnalysisTaskRepository analysisTaskRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisSegmentRepository analysisSegmentRepository;
    private final ReportRepository reportRepository;
    private final AudioRepository audioRepository;
    private final ObjectMapper objectMapper;
    private final WarningEventTriggerService warningEventTriggerService;

    public ResourceManagementService(AnalysisTaskRepository analysisTaskRepository,
                                     AnalysisResultRepository analysisResultRepository,
                                     AnalysisSegmentRepository analysisSegmentRepository,
                                     ReportRepository reportRepository,
                                     AudioRepository audioRepository,
                                     ObjectMapper objectMapper,
                                     WarningEventTriggerService warningEventTriggerService) {
        this.analysisTaskRepository = analysisTaskRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.analysisSegmentRepository = analysisSegmentRepository;
        this.reportRepository = reportRepository;
        this.audioRepository = audioRepository;
        this.objectMapper = objectMapper;
        this.warningEventTriggerService = warningEventTriggerService;
    }

    public TaskListResponse tasks(int page,
                                  int pageSize,
                                  String status,
                                  String keyword,
                                  String sortBy,
                                  String sortOrder,
                                  boolean adminView,
                                  long userId) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        try {
            int offset = (safePage - 1) * safeSize;
            long total = adminView
                    ? analysisTaskRepository.countTasks(status, keyword)
                    : analysisTaskRepository.countTasksByUser(userId, status, keyword);
            List<AnalysisTask> rows = adminView
                    ? analysisTaskRepository.findTaskPage(offset, safeSize, status, keyword, sortBy, sortOrder)
                    : analysisTaskRepository.findTaskPageByUser(userId, offset, safeSize, status, keyword, sortBy, sortOrder);
            List<TaskListResponse.TaskDTO> items = rows.stream()
                    .map(it -> new TaskListResponse.TaskDTO(
                            it.id(),
                            it.audioFileId(),
                            it.status(),
                            it.attemptCount(),
                            it.maxAttempts(),
                            it.errorMessage(),
                            it.traceId(),
                            format(it.createdAt()),
                            format(it.updatedAt()),
                            format(it.startedAt()),
                            format(it.finishedAt()),
                            it.durationMs(),
                            it.serLatencyMs(),
                            analysisResultRepository.findByTaskId(it.id()).map(r -> r.id()).orElse(null)
                    ))
                    .toList();
            return new TaskListResponse(total, safePage, safeSize, safeSize, items);
        } catch (Exception e) {
            log.warn("task list query failed, fallback to empty list", e);
            return new TaskListResponse(0, safePage, safeSize, safeSize, List.of());
        }
    }

    public ReportListResponse reports(int page,
                                      int pageSize,
                                      String riskLevel,
                                      String emotion,
                                      String keyword,
                                      String sortBy,
                                      String sortOrder,
                                      boolean adminView,
                                      long userId) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        try {
            int offset = (safePage - 1) * safeSize;
            long total = adminView
                    ? reportRepository.count(riskLevel, emotion, keyword)
                    : reportRepository.countByUser(userId, riskLevel, emotion, keyword);
            List<ReportResource> rows = adminView
                    ? reportRepository.page(riskLevel, emotion, keyword, offset, safeSize, sortBy, sortOrder)
                    : reportRepository.pageByUser(userId, riskLevel, emotion, keyword, offset, safeSize, sortBy, sortOrder);
            List<ReportListResponse.ReportDTO> items = rows.stream()
                    .map(this::toReportDTO)
                    .toList();
            return new ReportListResponse(total, safePage, safeSize, safeSize, items);
        } catch (Exception e) {
            log.warn("report list query failed, fallback to empty list", e);
            return new ReportListResponse(0, safePage, safeSize, safeSize, List.of());
        }
    }

    public ReportListResponse.ReportDTO report(long reportId, boolean adminView, long userId) {
        ReportResource report = (adminView
                ? reportRepository.findById(reportId)
                : reportRepository.findByIdForUser(reportId, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "report not found: " + reportId));
        return toReportDTO(report);
    }

    public Map<String, Object> reportTrend(long userId, int days) {
        int safeDays = Math.min(180, Math.max(1, days));
        List<Map<String, Object>> trendRows = reportRepository.listUserDailyTrend(userId, safeDays);
        List<Map<String, Object>> items = trendRows.stream()
                .map(this::toTrendItem)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("total", items.size());
        response.put("page", 1);
        response.put("pageSize", items.size());
        response.put("days", safeDays);
        return response;
    }

    public void deleteReport(long reportId) {
        int updated = reportRepository.softDelete(reportId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "report not found: " + reportId);
        }
    }

    public AudioListResponse audios(int page, int size, String q) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        long total = audioRepository.countManaged(q);
        List<AudioListResponse.Item> items = audioRepository.findManagedPage(offset, safeSize, q).stream()
                .map(a -> new AudioListResponse.Item(
                        a.id(),
                        a.userId(),
                        a.originalName(),
                        a.storedName(),
                        null,
                        a.sizeBytes(),
                        a.durationMs(),
                        a.status(),
                        format(a.createdAt())
                ))
                .toList();
        return new AudioListResponse(total, safePage, safeSize, items);
    }

    @Transactional
    public void deleteAudio(long audioId) {
        AudioFile audio = audioRepository.findById(audioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "audio not found: " + audioId));
        audioRepository.softDelete(audioId);
        analysisTaskRepository.markDeletedByAudioId(audioId);
        reportRepository.softDeleteByAudioId(audioId);
        if (audio.storagePath() != null) {
            try {
                Files.deleteIfExists(Path.of(audio.storagePath()));
            } catch (IOException e) {
                throw new IllegalStateException("failed to delete audio file: " + audio.storagePath(), e);
            }
        }
    }

    public AdminMetricsResponse metrics() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long total = analysisTaskRepository.countTasks(null);
        long active = analysisTaskRepository.countActiveTasks();
        long success = analysisTaskRepository.countInStatusSince("SUCCESS", since);
        long failed = analysisTaskRepository.countInStatusSince("FAILED", since);
        double rate = (success + failed) == 0 ? 0D : (double) success / (success + failed);
        long avg = Math.round(analysisTaskRepository.avgDurationSinceSuccess(since) == null ? 0D : analysisTaskRepository.avgDurationSinceSuccess(since));
        long timeoutCount = analysisTaskRepository.countSerTimeoutSince(since);
        return new AdminMetricsResponse(total, active, rate, avg, timeoutCount);
    }

    public void upsertReportResource(long taskId, long audioId, String rawJson, String defaultEmotion, Double confidence) {
        String riskLevel = "LOW";
        String overall = defaultEmotion;
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            JsonNode riskNode = node.at("/riskAssessment/risk_level");
            if (riskNode.isTextual()) {
                riskLevel = riskNode.asText();
            }
            JsonNode overallNode = node.at("/ser/overall/emotionCode");
            if (overallNode.isTextual()) {
                overall = overallNode.asText();
            }
        } catch (Exception ignored) {
        }
        reportRepository.upsert(taskId, audioId, rawJson, riskLevel, overall);
        warningEventTriggerService.tryCreateWarningEvent(taskId, audioId, rawJson, overall, riskLevel);
    }

    private ReportListResponse.ReportDTO toReportDTO(ReportResource report) {
        var audio = audioRepository.findById(report.audioId())
                .map(a -> new ReportListResponse.AudioMetaDTO(a.id(), a.originalName(), a.storedName(), a.contentType(), a.sizeBytes(), a.durationMs()))
                .orElse(null);
        String overall = report.overallEmotion();
        Double confidence = null;
        ReportListResponse.RiskDTO risk = new ReportListResponse.RiskDTO(0D, report.riskLevel());
        List<ReportListResponse.SegmentDTO> segments = List.of();
        try {
            JsonNode root = objectMapper.readTree(report.reportJson());
            JsonNode serOverall = root.at("/ser/overall");
            if (serOverall.hasNonNull("emotionCode")) {
                overall = serOverall.get("emotionCode").asText();
            }
            if (serOverall.has("confidence") && serOverall.get("confidence").isNumber()) {
                confidence = serOverall.get("confidence").asDouble();
            }
            JsonNode riskNode = root.at("/riskAssessment");
            if (riskNode.isObject()) {
                risk = new ReportListResponse.RiskDTO(riskNode.path("risk_score").asDouble(0D), riskNode.path("risk_level").asText(report.riskLevel()));
            }
            JsonNode arr = root.at("/ser/segments");
            if (arr.isArray()) {
                segments = new java.util.ArrayList<>();
                for (JsonNode n : arr) {
                    segments.add(new ReportListResponse.SegmentDTO(
                            n.path("startMs").asLong(),
                            n.path("endMs").asLong(),
                            n.path("emotionCode").asText(),
                            n.path("confidence").asDouble()
                    ));
                }
            }
        } catch (IOException ignored) {
        }

        return new ReportListResponse.ReportDTO(report.id(), report.taskId(), overall, segments, risk, confidence, format(report.createdAt()), audio);
    }

    private Map<String, Object> toTrendItem(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("date", asString(row.get("stat_date")));
        item.put("reportCount", toLong(row.get("report_count")));
        item.put("avgRiskScore", toRoundedDouble(row.get("avg_risk_score")));
        item.put("lowCount", toLong(row.get("low_count")));
        item.put("mediumCount", toLong(row.get("medium_count")));
        item.put("highCount", toLong(row.get("high_count")));
        return item;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private double toRoundedDouble(Object value) {
        double number = 0D;
        if (value instanceof Number numericValue) {
            number = numericValue.doubleValue();
        } else if (value != null) {
            try {
                number = Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                number = 0D;
            }
        }
        return Math.round(number * 100D) / 100D;
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(FMT);
    }
}
