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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ResourceManagementService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AnalysisTaskRepository analysisTaskRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisSegmentRepository analysisSegmentRepository;
    private final ReportRepository reportRepository;
    private final AudioRepository audioRepository;
    private final ObjectMapper objectMapper;

    public ResourceManagementService(AnalysisTaskRepository analysisTaskRepository,
                                     AnalysisResultRepository analysisResultRepository,
                                     AnalysisSegmentRepository analysisSegmentRepository,
                                     ReportRepository reportRepository,
                                     AudioRepository audioRepository,
                                     ObjectMapper objectMapper) {
        this.analysisTaskRepository = analysisTaskRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.analysisSegmentRepository = analysisSegmentRepository;
        this.reportRepository = reportRepository;
        this.audioRepository = audioRepository;
        this.objectMapper = objectMapper;
    }

    public TaskListResponse tasks(int page, int size, String status, String sort) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        long total = analysisTaskRepository.countTasks(status);
        List<TaskListResponse.TaskDTO> items = analysisTaskRepository.findTaskPage(offset, safeSize, status, sort).stream()
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
        return new TaskListResponse(total, safePage, safeSize, items);
    }

    public ReportListResponse reports(int page, int size, String riskLevel, String emotion, String q) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        long total = reportRepository.count(riskLevel, emotion, q);
        List<ReportListResponse.ReportDTO> items = reportRepository.page(riskLevel, emotion, q, offset, safeSize).stream()
                .map(this::toReportDTO)
                .toList();
        return new ReportListResponse(total, safePage, safeSize, items);
    }

    public ReportListResponse.ReportDTO report(long reportId) {
        ReportResource report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "report 不存在: " + reportId));
        return toReportDTO(report);
    }

    public void deleteReport(long reportId) {
        int updated = reportRepository.softDelete(reportId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "report 不存在: " + reportId);
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "audio 不存在: " + audioId));
        audioRepository.softDelete(audioId);
        analysisTaskRepository.markDeletedByAudioId(audioId);
        reportRepository.softDeleteByAudioId(audioId);
        if (audio.storagePath() != null) {
            try {
                Files.deleteIfExists(Path.of(audio.storagePath()));
            } catch (IOException e) {
                throw new IllegalStateException("删除音频文件失败: " + audio.storagePath(), e);
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
            if (riskNode.isTextual()) riskLevel = riskNode.asText();
            JsonNode overallNode = node.at("/ser/overall/emotionCode");
            if (overallNode.isTextual()) overall = overallNode.asText();
        } catch (Exception ignored) {
        }
        reportRepository.upsert(taskId, audioId, rawJson, riskLevel, overall);
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
            if (serOverall.hasNonNull("emotionCode")) overall = serOverall.get("emotionCode").asText();
            if (serOverall.has("confidence") && serOverall.get("confidence").isNumber()) confidence = serOverall.get("confidence").asDouble();
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

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(FMT);
    }
}
