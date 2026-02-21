package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.dto.response.AnalysisSegmentsResponse;
import com.wuhao.aiemotion.dto.response.AnalysisTaskResultResponse;
import com.wuhao.aiemotion.dto.response.AnalysisTaskStatusResponse;
import com.wuhao.aiemotion.dto.response.TaskRealtimeSnapshotResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TaskRealtimeSnapshotService {

    private static final int CURVE_LIMIT = 180;
    private static final Set<String> TERMINAL_STATUSES = Set.of("SUCCESS", "FAILED", "CANCELED");

    private final AnalysisTaskService analysisTaskService;
    private final TaskRealtimeProgressTracker progressTracker;

    public TaskRealtimeSnapshotService(AnalysisTaskService analysisTaskService,
                                       TaskRealtimeProgressTracker progressTracker) {
        this.analysisTaskService = analysisTaskService;
        this.progressTracker = progressTracker;
    }

    public TaskRealtimeSnapshotResponse buildSnapshot(long taskId, AuthService.UserProfile user) {
        AnalysisTaskStatusResponse task = analysisTaskService.getTask(taskId, user);
        TaskRealtimeSnapshotResponse.RiskSummary riskSummary = mapRisk(task);
        TaskRealtimeSnapshotResponse.ProgressSummary progressSummary = progressTracker.current(taskId)
                .map(it -> new TaskRealtimeSnapshotResponse.ProgressSummary(
                        it.phase(),
                        it.message(),
                        it.sequence(),
                        it.emittedAtMs(),
                        it.details()
                ))
                .orElse(null);

        List<TaskRealtimeSnapshotResponse.RiskCurvePoint> curve = buildCurve(taskId, task.status(), user);
        return new TaskRealtimeSnapshotResponse(
                "snapshot",
                task.taskId(),
                task.task_no(),
                task.status(),
                task.attempt_count(),
                task.max_attempts(),
                task.trace_id(),
                task.next_run_at(),
                task.updated_at(),
                task.error_message(),
                TERMINAL_STATUSES.contains(task.status()),
                riskSummary,
                progressSummary,
                curve
        );
    }

    private List<TaskRealtimeSnapshotResponse.RiskCurvePoint> buildCurve(long taskId,
                                                                          String status,
                                                                          AuthService.UserProfile user) {
        if (!"SUCCESS".equals(status)) {
            return List.of();
        }
        try {
            AnalysisSegmentsResponse segments = analysisTaskService.getTaskSegments(taskId, 0L, null, CURVE_LIMIT, 0, user);
            List<AnalysisTaskResultResponse.AnalysisSegmentPayload> rows = segments.items();
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            java.util.ArrayList<TaskRealtimeSnapshotResponse.RiskCurvePoint> points = new java.util.ArrayList<>(rows.size());
            for (int index = 0; index < rows.size(); index++) {
                points.add(toCurvePoint(rows.get(index), index));
            }
            return points;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return List.of();
            }
            throw ex;
        }
    }

    private TaskRealtimeSnapshotResponse.RiskCurvePoint toCurvePoint(
            AnalysisTaskResultResponse.AnalysisSegmentPayload segment,
            int index
    ) {
        double confidence = clamp01(segment.confidence());
        double riskIndex = round2(confidence * emotionRiskBase(segment.emotion_code()) * 100.0D);
        return new TaskRealtimeSnapshotResponse.RiskCurvePoint(
                index,
                segment.start_ms(),
                segment.end_ms(),
                segment.emotion_code(),
                round4(confidence),
                riskIndex
        );
    }

    private TaskRealtimeSnapshotResponse.RiskSummary mapRisk(AnalysisTaskStatusResponse task) {
        if (task.overall() == null || task.overall().risk_assessment() == null) {
            return null;
        }
        AnalysisTaskResultResponse.RiskAssessmentPayload risk = task.overall().risk_assessment();
        return new TaskRealtimeSnapshotResponse.RiskSummary(
                round4(risk.risk_score()),
                risk.risk_level(),
                round4(risk.p_sad()),
                round4(risk.p_angry()),
                round4(risk.var_conf()),
                round4(risk.text_neg())
        );
    }

    private double emotionRiskBase(String emotionCode) {
        if (emotionCode == null || emotionCode.isBlank()) {
            return 0.45D;
        }
        String normalized = emotionCode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SAD", "SADNESS" -> 1.00D;
            case "ANG", "ANGRY", "ANGER" -> 0.85D;
            case "NEU", "NEUTRAL" -> 0.35D;
            case "HAP", "HAPPY", "JOY", "POSITIVE" -> 0.10D;
            default -> 0.45D;
        };
    }

    private double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0D) / 10000.0D;
    }
}
