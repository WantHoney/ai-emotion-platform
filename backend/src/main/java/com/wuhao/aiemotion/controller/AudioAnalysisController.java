package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.dto.response.AnalysisSegmentsResponse;
import com.wuhao.aiemotion.dto.response.AnalysisTaskResultResponse;
import com.wuhao.aiemotion.dto.response.AnalysisTaskStartResponse;
import com.wuhao.aiemotion.dto.response.AnalysisTaskStatusResponse;
import com.wuhao.aiemotion.dto.response.AudioAnalysisAdminListResponse;
import com.wuhao.aiemotion.dto.response.AudioAnalysisDeleteResponse;
import com.wuhao.aiemotion.dto.response.AudioAnalysisDetailResponse;
import com.wuhao.aiemotion.dto.response.AudioAnalysisListResponse;
import com.wuhao.aiemotion.dto.response.AudioAnalysisReportResponse;
import com.wuhao.aiemotion.dto.response.AudioAnalysisRunResponse;
import com.wuhao.aiemotion.service.AnalysisTaskService;
import com.wuhao.aiemotion.service.AudioAnalysisService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AudioAnalysisController {

    private final AudioAnalysisService audioAnalysisService;
    private final AnalysisTaskService analysisTaskService;

    public AudioAnalysisController(AudioAnalysisService audioAnalysisService,
                                   AnalysisTaskService analysisTaskService) {
        this.audioAnalysisService = audioAnalysisService;
        this.analysisTaskService = analysisTaskService;
    }

    @PostMapping("/api/audio/{audioId}/analysis/start")
    public AnalysisTaskStartResponse start(@PathVariable long audioId) {
        return analysisTaskService.startTask(audioId);
    }

    @GetMapping("/api/analysis/task/{taskId}")
    public AnalysisTaskStatusResponse getTask(@PathVariable long taskId) {
        return analysisTaskService.getTask(taskId);
    }

    @GetMapping("/api/analysis/task/{taskId}/result")
    public AnalysisTaskResultResponse getTaskResult(@PathVariable long taskId) {
        return analysisTaskService.getTaskResult(taskId);
    }

    @GetMapping("/api/analysis/task/{taskId}/segments")
    public AnalysisSegmentsResponse getTaskSegments(
            @PathVariable long taskId,
            @RequestParam(required = false) Long fromMs,
            @RequestParam(required = false) Long toMs,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return analysisTaskService.getTaskSegments(taskId, fromMs, toMs, limit, offset);
    }

    @GetMapping("/api/analysis/list")
    public AudioAnalysisAdminListResponse adminList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status
    ) {
        return audioAnalysisService.adminList(page, size, status);
    }

    @GetMapping("/api/analysis/{analysisId:\\d+}")
    public AudioAnalysisDetailResponse detail(@PathVariable long analysisId) {
        return audioAnalysisService.detail(analysisId);
    }

    @GetMapping("/api/analysis/{analysisId:\\d+}/report")
    public AudioAnalysisReportResponse report(@PathVariable long analysisId) {
        return audioAnalysisService.report(analysisId);
    }

    @PostMapping("/api/analysis/{analysisId:\\d+}/report/generate")
    public AudioAnalysisReportResponse generateReport(@PathVariable long analysisId) {
        return audioAnalysisService.generateAndSaveReport(analysisId);
    }

    @GetMapping("/api/audio/{audioId}/analysis/latest")
    public AudioAnalysisDetailResponse latest(@PathVariable long audioId) {
        return audioAnalysisService.latestByAudio(audioId);
    }

    @GetMapping("/api/audio/{audioId}/analysis/list")
    public AudioAnalysisListResponse list(
            @PathVariable long audioId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return audioAnalysisService.listByAudio(audioId, page, size);
    }

    @PostMapping("/api/analysis/{analysisId:\\d+}/run")
    public AudioAnalysisReportResponse run(@PathVariable long analysisId) {
        return audioAnalysisService.run(analysisId);
    }

    @PostMapping("/api/analysis/{analysisId:\\d+}/run-async")
    public AudioAnalysisRunResponse runAsync(@PathVariable long analysisId) {
        return audioAnalysisService.runAsync(analysisId);
    }

    @DeleteMapping("/api/analysis/{analysisId:\\d+}")
    public AudioAnalysisDeleteResponse delete(@PathVariable long analysisId) {
        return audioAnalysisService.deleteAnalysis(analysisId);
    }
}
