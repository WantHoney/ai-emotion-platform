package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.domain.AnalysisSegment;
import com.wuhao.aiemotion.domain.AnalysisTask;
import com.wuhao.aiemotion.dto.response.AnalysisTaskResultResponse;
import com.wuhao.aiemotion.integration.asr.AsrClient;
import com.wuhao.aiemotion.integration.asr.AsrTranscribeResponse;
import com.wuhao.aiemotion.integration.ser.SerAnalyzeResponse;
import com.wuhao.aiemotion.integration.ser.SerClient;
import com.wuhao.aiemotion.integration.ser.SerClientException;
import com.wuhao.aiemotion.integration.text.TextSentimentClient;
import com.wuhao.aiemotion.integration.text.TextSentimentResponse;
import com.wuhao.aiemotion.repository.AnalysisResultRepository;
import com.wuhao.aiemotion.repository.AnalysisSegmentRepository;
import com.wuhao.aiemotion.repository.AnalysisTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisTaskWorkerService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisTaskWorkerService.class);
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final int TIMEOUT_RETRY_BACKOFF_SECONDS = 180;
    private static final double TEXT_MODEL_WEIGHT = 0.75D;
    private static final double TEXT_LEXICON_WEIGHT = 0.25D;

    private final AnalysisTaskRepository analysisTaskRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisSegmentRepository analysisSegmentRepository;
    private final SerClient serClient;
    private final AsrClient asrClient;
    private final TextSentimentClient textSentimentClient;
    private final PsychologicalRiskScoringService riskScoringService;
    private final TextNegScorer textNegScorer;
    private final AnalysisWorkerProperties workerProperties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ResourceManagementService resourceManagementService;

    public AnalysisTaskWorkerService(AnalysisTaskRepository analysisTaskRepository,
                                     AnalysisResultRepository analysisResultRepository,
                                     AnalysisSegmentRepository analysisSegmentRepository,
                                     SerClient serClient,
                                     AsrClient asrClient,
                                     TextSentimentClient textSentimentClient,
                                     PsychologicalRiskScoringService riskScoringService,
                                     TextNegScorer textNegScorer,
                                     AnalysisWorkerProperties workerProperties,
                                     ObjectMapper objectMapper,
                                     TransactionTemplate transactionTemplate,
                                     ResourceManagementService resourceManagementService) {
        this.analysisTaskRepository = analysisTaskRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.analysisSegmentRepository = analysisSegmentRepository;
        this.serClient = serClient;
        this.asrClient = asrClient;
        this.textSentimentClient = textSentimentClient;
        this.riskScoringService = riskScoringService;
        this.textNegScorer = textNegScorer;
        this.workerProperties = workerProperties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.resourceManagementService = resourceManagementService;
    }

    public List<AnalysisTask> findCandidates() {
        return analysisTaskRepository.findRunnableCandidates(workerProperties.getBatchSize());
    }

    public int claim(long taskId, String workerId) {
        return analysisTaskRepository.claimTask(taskId, workerId);
    }

    public void processClaimedTask(AnalysisTask task, String workerId) {
        String traceId = String.valueOf(task.id());
        MDC.put("traceId", traceId);
        try {
            long audioId = task.audioFileId() == null ? -1 : task.audioFileId();
            String audioPath = analysisTaskRepository.findAudioStoragePath(task.id())
                    .orElseThrow(() -> new IllegalStateException("audio path not found, taskId=" + task.id()));

            log.info("analysis task processing start: taskId={}, audioId={}, status=PROCESSING, attemptCount={}",
                    task.id(), audioId, task.attemptCount());

            AsrTranscribeResponse asrResponse = null;
            long asrCostMs = -1;
            boolean asrFailed = false;
            Instant asrStarted = Instant.now();
            try {
                asrResponse = asrClient.transcribe(Path.of(audioPath), workerProperties.getAsrTimeoutMs());
                asrCostMs = Duration.between(asrStarted, Instant.now()).toMillis();
            } catch (Exception ex) {
                asrFailed = true;
                asrCostMs = Duration.between(asrStarted, Instant.now()).toMillis();
                log.warn("asr transcribe failed, fallback to voice-only risk: taskId={}, audioId={}, asrCostMs={}, reason={}",
                        task.id(), audioId, asrCostMs, truncateError(ex.getMessage()));
            }
            String languageHint = normalizeLanguageHint(asrResponse == null ? null : asrResponse.language());
            String transcript = asrResponse == null ? "" : asrResponse.text();
            TextNegScorer.TextNegScoreResult lexiconTextScore = textNegScorer.score(transcript);
            TextSentimentResponse textSentiment = null;
            try {
                if (transcript != null && !transcript.isBlank()) {
                    textSentiment = textSentimentClient.score(
                            transcript,
                            languageHint,
                            workerProperties.getTextSentimentTimeoutMs()
                    );
                }
            } catch (Exception ex) {
                log.warn("text sentiment failed, fallback to lexicon only: taskId={}, audioId={}, reason={}",
                        task.id(), audioId, truncateError(ex.getMessage()));
            }
            double fusedTextNeg = fuseTextNeg(
                    lexiconTextScore.textNeg(),
                    textSentiment == null ? null : textSentiment.negativeScore()
            );
            SerClient.FusionTextFeatures fusionTextFeatures =
                    buildSerFusionTextFeatures(transcript, textSentiment, fusedTextNeg);

            Instant started = Instant.now();
            SerAnalyzeResponse response = serClient.analyze(Path.of(audioPath), languageHint, fusionTextFeatures);
            long serCostMs = Duration.between(started, Instant.now()).toMillis();

            AnalysisTaskResultResponse.RiskAssessmentPayload riskAssessment =
                    riskScoringService.evaluate(toSegments(task.id(), response), fusedTextNeg);

            AsrTranscribeResponse finalAsrResponse = asrResponse;
            final long finalSerCostMs = serCostMs;
            TextSentimentResponse finalTextSentiment = textSentiment;
            transactionTemplate.executeWithoutResult(s -> saveSuccessResult(
                    task, workerId, response, finalAsrResponse, transcript,
                    lexiconTextScore, finalTextSentiment, fusedTextNeg, riskAssessment, finalSerCostMs
            ));
            log.info("analysis task success: taskId={}, audioId={}, status=SUCCESS, attemptCount={}, serCostMs={}, asrCostMs={}, asrFailed={}, asrLanguageHint={}, textLength={}, textNegLexicon={}, textNegModel={}, textNegFused={}, finalRisk={}, fusionReady={}, fusionLabel={}",
                    task.id(), audioId, task.attemptCount(), serCostMs, asrCostMs, asrFailed,
                    languageHint,
                    transcript == null ? 0 : transcript.length(),
                    lexiconTextScore.textNeg(),
                    textSentiment == null ? null : textSentiment.negativeScore(),
                    fusedTextNeg,
                    riskAssessment.risk_score(),
                    response.fusion() == null ? null : response.fusion().ready(),
                    response.fusion() == null ? null : response.fusion().label());
        } catch (Exception e) {
            transactionTemplate.executeWithoutResult(s -> handleFailure(task, workerId, e));
        } finally {
            MDC.remove("traceId");
        }
    }

    protected void saveSuccessResult(AnalysisTask task,
                                     String workerId,
                                     SerAnalyzeResponse response,
                                     AsrTranscribeResponse asrResponse,
                                     String transcript,
                                     TextNegScorer.TextNegScoreResult lexiconTextScore,
                                     TextSentimentResponse textSentiment,
                                     double fusedTextNeg,
                                     AnalysisTaskResultResponse.RiskAssessmentPayload riskAssessment,
                                     long serLatencyMs) {
        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(new WorkerPersistedPayload(
                    response,
                    asrResponse,
                    transcript,
                    lexiconTextScore,
                    textSentiment,
                    new TextNegFusionPayload(round4(fusedTextNeg), TEXT_LEXICON_WEIGHT, TEXT_MODEL_WEIGHT),
                    riskAssessment
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize SER response for taskId=" + task.id(), e);
        }
        analysisResultRepository.upsertByTaskId(
                task.id(),
                response.meta() == null ? null : response.meta().model(),
                response.overall() == null ? null : response.overall().emotionCode(),
                response.overall() == null ? null : response.overall().confidence(),
                response.meta() == null ? null : Math.toIntExact(response.meta().durationMs()),
                response.meta() == null ? null : response.meta().sampleRate(),
                rawJson
        );

        analysisSegmentRepository.deleteByTaskId(task.id());
        List<AnalysisSegment> segments = toSegments(task.id(), response);
        analysisSegmentRepository.batchInsert(task.id(), segments);
        analysisTaskRepository.markSuccess(task.id(), workerId, serLatencyMs);
        if (task.audioFileId() == null) {
            throw new IllegalStateException("audio id missing for task=" + task.id());
        }
        resourceManagementService.upsertReportResource(task.id(), task.audioFileId(), rawJson,
                response.overall() == null ? null : response.overall().emotionCode(),
                response.overall() == null ? null : response.overall().confidence());
    }

    private List<AnalysisSegment> toSegments(long taskId, SerAnalyzeResponse response) {
        return response.segments() == null ? List.of() : response.segments().stream()
                .map(it -> new AnalysisSegment(
                        0,
                        taskId,
                        Math.toIntExact(it.startMs()),
                        Math.toIntExact(it.endMs()),
                        it.emotionCode(),
                        it.confidence(),
                        null
                ))
                .toList();
    }

    protected void handleFailure(AnalysisTask task, String workerId, Exception e) {
        int nextAttempt = task.attemptCount() + 1;
        String category = classifyError(e);
        boolean timeoutFailure = "timeout".equals(category);
        int backoffSeconds = timeoutFailure
                ? Math.max(computeBackoffSeconds(nextAttempt), TIMEOUT_RETRY_BACKOFF_SECONDS)
                : computeBackoffSeconds(nextAttempt);
        String error = ("timeout".equals(category) ? "TIMEOUT:" : category.toUpperCase()+":") + truncateError(e.getMessage());
        int updated = analysisTaskRepository.markRetryOrFailed(
                task.id(),
                workerId,
                workerProperties.getMaxAttempts(),
                error,
                backoffSeconds
        );
        if (updated == 0) {
            log.warn("analysis task failure state update skipped: taskId={}, workerId={}", task.id(), workerId);
            return;
        }

        String status = nextAttempt >= workerProperties.getMaxAttempts() ? "FAILED" : "RETRY_WAIT";
        log.error("analysis task failed: taskId={}, audioId={}, status={}, attemptCount={}, backoffSeconds={}, reason={}",
                task.id(), task.audioFileId(), status, nextAttempt, backoffSeconds, error, e);
    }


    private String classifyError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SerClientException serClientException) {
                return serClientException.getCategory();
            }
            current = current.getCause();
        }
        return isTimeoutFailure(throwable) ? "timeout" : "unknown";
    }
    private boolean isTimeoutFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            if (current instanceof ResourceAccessException
                    && current.getCause() instanceof SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("timed out") || lower.contains("timeout")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private int computeBackoffSeconds(int attempt) {
        int seconds;
        if (attempt <= 1) {
            seconds = workerProperties.getBackoffBaseSeconds();
        } else if (attempt == 2) {
            seconds = workerProperties.getBackoffBaseSeconds() * 4;
        } else {
            seconds = workerProperties.getBackoffBaseSeconds() * 20;
        }
        return Math.min(seconds, workerProperties.getBackoffMaxSeconds());
    }

    private SerClient.FusionTextFeatures buildSerFusionTextFeatures(String transcript,
                                                                    TextSentimentResponse textSentiment,
                                                                    double fusedTextNeg) {
        double negative = clamp01(fusedTextNeg);
        double neutral = Math.max(0.0D, 1.0D - negative);
        double positive = 0.0D;
        Double negativeScore = null;

        if (textSentiment != null) {
            if (textSentiment.negativeScore() != null) {
                negativeScore = clamp01(textSentiment.negativeScore());
                negative = negativeScore;
            }
            Map<String, Double> scores = textSentiment.scores();
            if (scores != null && !scores.isEmpty()) {
                negative = clamp01(scoreFromMap(scores, "negative", negative));
                neutral = clamp01(scoreFromMap(scores, "neutral", neutral));
                positive = clamp01(scoreFromMap(scores, "positive", positive));
                double total = negative + neutral + positive;
                if (total > 0.0D) {
                    negative /= total;
                    neutral /= total;
                    positive /= total;
                }
            } else {
                neutral = Math.max(0.0D, 1.0D - negative);
                positive = 0.0D;
            }
        }

        if (negativeScore == null) {
            negativeScore = negative;
        }
        double textLengthNorm = clamp01((transcript == null ? 0.0D : transcript.length() / 256.0D));

        return new SerClient.FusionTextFeatures(
                round4(negative),
                round4(neutral),
                round4(positive),
                round4(negativeScore),
                round4(textLengthNorm)
        );
    }

    private double scoreFromMap(Map<String, Double> scores, String key, double fallback) {
        if (scores == null || scores.isEmpty()) {
            return fallback;
        }
        Double value = scores.get(key);
        if (value == null) {
            value = scores.get(key.toUpperCase());
        }
        return value == null ? fallback : value;
    }

    private double fuseTextNeg(double lexiconScore, Double modelScore) {
        double lexical = clamp01(lexiconScore);
        if (modelScore == null) {
            return round4(lexical);
        }
        double model = clamp01(modelScore);
        return round4(TEXT_LEXICON_WEIGHT * lexical + TEXT_MODEL_WEIGHT * model);
    }

    private String normalizeLanguageHint(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String normalized = language.trim().toLowerCase();
        if (normalized.startsWith("zh")) {
            return "zh";
        }
        if (normalized.startsWith("en")) {
            return "en";
        }
        return null;
    }

    private double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private double round4(double value) {
        return Math.round(value * 10000.0D) / 10000.0D;
    }

    private String truncateError(String value) {
        String message = value == null || value.isBlank() ? "unknown error" : value;
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    private record WorkerPersistedPayload(
            SerAnalyzeResponse ser,
            AsrTranscribeResponse asr,
            String transcript,
            TextNegScorer.TextNegScoreResult textNeg,
            TextSentimentResponse textSentiment,
            TextNegFusionPayload textNegFusion,
            AnalysisTaskResultResponse.RiskAssessmentPayload riskAssessment
    ) {
    }

    private record TextNegFusionPayload(
            double fusedTextNeg,
            double lexiconWeight,
            double modelWeight
    ) {
    }
}
