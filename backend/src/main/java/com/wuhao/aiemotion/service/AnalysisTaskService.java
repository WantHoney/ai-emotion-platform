package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.domain.AnalysisResult;
import com.wuhao.aiemotion.domain.AnalysisSegment;
import com.wuhao.aiemotion.domain.AnalysisTask;
import com.wuhao.aiemotion.dto.response.AnalysisSegmentsResponse;
import com.wuhao.aiemotion.dto.response.AnalysisTaskResultResponse;
import com.wuhao.aiemotion.dto.response.AnalysisTaskStartResponse;
import com.wuhao.aiemotion.dto.response.AnalysisTaskStatusResponse;
import com.wuhao.aiemotion.repository.AnalysisResultRepository;
import com.wuhao.aiemotion.repository.AnalysisSegmentRepository;
import com.wuhao.aiemotion.repository.AnalysisTaskRepository;
import com.wuhao.aiemotion.repository.AudioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AnalysisTaskService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(AnalysisTaskService.class);
    private static final int DEFAULT_SEGMENTS_LIMIT = 50;
    private static final int MAX_SEGMENTS_LIMIT = 500;

    private final AnalysisTaskRepository analysisTaskRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisSegmentRepository analysisSegmentRepository;
    private final AudioRepository audioRepository;
    private final PsychologicalRiskScoringService riskScoringService;
    private final ObjectMapper objectMapper;
    private final AnalysisWorkerProperties workerProperties;
    private final TaskNoFormatter taskNoFormatter;

    public AnalysisTaskService(AnalysisTaskRepository analysisTaskRepository,
                               AnalysisResultRepository analysisResultRepository,
                               AnalysisSegmentRepository analysisSegmentRepository,
                               AudioRepository audioRepository,
                               PsychologicalRiskScoringService riskScoringService,
                               ObjectMapper objectMapper,
                               AnalysisWorkerProperties workerProperties,
                               TaskNoFormatter taskNoFormatter) {
        this.analysisTaskRepository = analysisTaskRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.analysisSegmentRepository = analysisSegmentRepository;
        this.audioRepository = audioRepository;
        this.riskScoringService = riskScoringService;
        this.objectMapper = objectMapper;
        this.workerProperties = workerProperties;
        this.taskNoFormatter = taskNoFormatter;
    }

    public AnalysisTaskStartResponse startTask(long audioId, AuthService.UserProfile user) {
        if (!audioRepository.existsById(audioId)) {
            throw new IllegalArgumentException("audio_id not found: " + audioId);
        }
        Long ownerUserId = audioRepository.findUserIdByAudioId(audioId).orElse(null);
        ensureCanAccess(ownerUserId, user);

        String traceId = MDC.get("traceId");
        long taskId = analysisTaskRepository.insertPendingTask(audioId, workerProperties.getMaxAttempts(), traceId);
        AnalysisTask task = analysisTaskRepository.findById(taskId).orElse(null);
        String taskNo = taskNoFormatter.format(ownerUserId, task == null ? null : task.createdAt(), taskId);
        return new AnalysisTaskStartResponse(taskId, taskNo, "PENDING");
    }

    public AnalysisTaskStatusResponse getTask(long taskId, AuthService.UserProfile user) {
        AnalysisTask task = findTaskOr404(taskId);
        Long ownerUserId = analysisTaskRepository.findUserIdByTaskId(taskId).orElse(null);
        ensureCanAccess(ownerUserId, user);

        AnalysisTaskStatusResponse.OverallSummary overall = null;
        if ("SUCCESS".equals(task.status())) {
            overall = analysisResultRepository.findByTaskId(taskId)
                    .map(result -> {
                        List<AnalysisSegment> segments = analysisSegmentRepository.findByTaskIdOrderByStartMs(taskId);
                        AnalysisTaskResultResponse.RiskAssessmentPayload risk = buildRiskAssessment(taskId, result, segments);
                        return toOverallSummary(result, risk);
                    })
                    .orElse(null);
        }

        return new AnalysisTaskStatusResponse(
                task.id(),
                taskNoFormatter.format(ownerUserId, task.createdAt(), task.id()),
                task.status(),
                task.attemptCount(),
                task.maxAttempts(),
                task.traceId(),
                format(task.nextRunAt()),
                task.errorMessage(),
                format(task.startedAt()),
                format(task.finishedAt()),
                format(task.createdAt()),
                format(task.updatedAt()),
                overall
        );
    }

    public AnalysisTaskResultResponse getTaskResult(long taskId, AuthService.UserProfile user) {
        AnalysisTask task = findTaskOr404(taskId);
        Long ownerUserId = analysisTaskRepository.findUserIdByTaskId(task.id()).orElse(null);
        ensureCanAccess(ownerUserId, user);

        AnalysisResult result = analysisResultRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "analysis_result not found: " + taskId));

        List<AnalysisSegment> allSegments = analysisSegmentRepository.findByTaskIdOrderByStartMs(taskId);
        AnalysisTaskResultResponse.RiskAssessmentPayload risk = buildRiskAssessment(taskId, result, allSegments);

        long toMs = resolveDefaultToMs(result);
        long total = analysisSegmentRepository.countSegmentsInRange(taskId, 0, toMs);
        List<AnalysisTaskResultResponse.AnalysisSegmentPayload> segments = analysisSegmentRepository
                .findSegmentsInRange(taskId, 0, toMs, DEFAULT_SEGMENTS_LIMIT, 0)
                .stream()
                .map(this::toSegmentPayload)
                .toList();

        return new AnalysisTaskResultResponse(
                toResultPayload(result, risk),
                segments,
                total,
                total > segments.size()
        );
    }

    public AnalysisSegmentsResponse getTaskSegments(long taskId,
                                                    Long fromMs,
                                                    Long toMs,
                                                    Integer limit,
                                                    Integer offset,
                                                    AuthService.UserProfile user) {
        AnalysisTask task = findTaskOr404(taskId);
        Long ownerUserId = analysisTaskRepository.findUserIdByTaskId(task.id()).orElse(null);
        ensureCanAccess(ownerUserId, user);

        AnalysisResult result = analysisResultRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "analysis_result not found: " + taskId));

        long safeFromMs = fromMs == null ? 0 : fromMs;
        long safeToMs = toMs == null ? resolveDefaultToMs(result) : toMs;
        int safeLimit = limit == null ? DEFAULT_SEGMENTS_LIMIT : Math.min(Math.max(limit, 1), MAX_SEGMENTS_LIMIT);
        int safeOffset = offset == null ? 0 : offset;

        validateRangeParams(safeFromMs, safeToMs, safeOffset);

        long total = analysisSegmentRepository.countSegmentsInRange(taskId, safeFromMs, safeToMs);
        List<AnalysisTaskResultResponse.AnalysisSegmentPayload> items = analysisSegmentRepository
                .findSegmentsInRange(taskId, safeFromMs, safeToMs, safeLimit, safeOffset)
                .stream()
                .map(this::toSegmentPayload)
                .toList();

        return new AnalysisSegmentsResponse(
                taskId,
                safeFromMs,
                safeToMs,
                safeLimit,
                safeOffset,
                total,
                items
        );
    }

    private void validateRangeParams(long fromMs, long toMs, int offset) {
        if (fromMs < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromMs must be >= 0");
        }
        if (toMs <= fromMs) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toMs must be > fromMs");
        }
        if (offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offset must be >= 0");
        }
    }

    private long resolveDefaultToMs(AnalysisResult result) {
        if (result.durationMs() != null && result.durationMs() > 0) {
            return result.durationMs();
        }
        return Long.MAX_VALUE;
    }

    private AnalysisTask findTaskOr404(long taskId) {
        return analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found: " + taskId));
    }

    private void ensureCanAccess(Long ownerUserId, AuthService.UserProfile user) {
        if (user == null) return;
        if (AuthService.ROLE_ADMIN.equals(user.role())) return;
        if (ownerUserId == null) return;
        if (!ownerUserId.equals(user.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "no permission for this task");
        }
    }

    private AnalysisTaskStatusResponse.OverallSummary toOverallSummary(AnalysisResult r,
                                                                       AnalysisTaskResultResponse.RiskAssessmentPayload risk) {
        return new AnalysisTaskStatusResponse.OverallSummary(
                r.overallEmotionCode(),
                r.overallConfidence(),
                r.durationMs(),
                r.sampleRate(),
                r.modelName(),
                risk
        );
    }

    private AnalysisTaskResultResponse.AnalysisResultPayload toResultPayload(AnalysisResult r,
                                                                             AnalysisTaskResultResponse.RiskAssessmentPayload risk) {
        return new AnalysisTaskResultResponse.AnalysisResultPayload(
                r.id(),
                r.taskId(),
                r.modelName(),
                r.overallEmotionCode(),
                r.overallConfidence(),
                r.durationMs(),
                r.sampleRate(),
                r.rawJson(),
                resolveTranscript(r),
                risk,
                format(r.createdAt())
        );
    }

    private AnalysisTaskResultResponse.RiskAssessmentPayload buildRiskAssessment(long taskId,
                                                                                 AnalysisResult result,
                                                                                 List<AnalysisSegment> segments) {
        double textNeg = resolveTextNeg(result);
        AnalysisTaskResultResponse.RiskAssessmentPayload risk = riskScoringService.evaluate(segments, textNeg);
        log.info("risk scoring computed in API layer (not persisted): taskId={}, riskScore={}, riskLevel={}, pSad={}, pAngry={}, varConf={}, textNeg={}",
                taskId,
                risk.risk_score(),
                risk.risk_level(),
                risk.p_sad(),
                risk.p_angry(),
                risk.var_conf(),
                risk.text_neg());
        return risk;
    }

    private double resolveTextNeg(AnalysisResult result) {
        return readDouble(result.rawJson(), "/textNeg/textNeg")
                .or(() -> readDouble(result.rawJson(), "/riskAssessment/text_neg"))
                .orElse(0.0D);
    }

    private String resolveTranscript(AnalysisResult result) {
        return readText(result.rawJson(), "/transcript")
                .or(() -> readText(result.rawJson(), "/asr/text"))
                .orElse(null);
    }

    private java.util.Optional<Double> readDouble(String rawJson, String pointer) {
        if (rawJson == null || rawJson.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson).at(pointer);
            return node.isNumber() ? java.util.Optional.of(node.doubleValue()) : java.util.Optional.empty();
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<String> readText(String rawJson, String pointer) {
        if (rawJson == null || rawJson.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson).at(pointer);
            if (!node.isTextual()) {
                return java.util.Optional.empty();
            }
            String value = node.textValue();
            return (value == null || value.isBlank()) ? java.util.Optional.empty() : java.util.Optional.of(value);
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private AnalysisTaskResultResponse.AnalysisSegmentPayload toSegmentPayload(AnalysisSegment s) {
        return new AnalysisTaskResultResponse.AnalysisSegmentPayload(
                s.id(),
                s.taskId(),
                s.startMs(),
                s.endMs(),
                s.emotionCode(),
                s.confidence(),
                format(s.createdAt())
        );
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.format(FMT);
    }
}
