package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.dto.response.AudioAnalysisReportResponse;
import com.wuhao.aiemotion.dto.response.AudioAnalysisRunResponse;
import com.wuhao.aiemotion.service.AudioAnalysisService;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

@RestController
@Conditional(MockAudioAnalysisController.MockEndpointCondition.class)
public class MockAudioAnalysisController {

    private final AudioAnalysisService audioAnalysisService;

    public MockAudioAnalysisController(AudioAnalysisService audioAnalysisService) {
        this.audioAnalysisService = audioAnalysisService;
    }

    @PostMapping("/api/analysis/{analysisId:\\d+}/mock-success")
    public void mockSuccess(@PathVariable long analysisId) {
        audioAnalysisService.mockSuccess(analysisId);
    }

    @PostMapping("/api/analysis/{analysisId:\\d+}/mock-fail")
    public void mockFail(@PathVariable long analysisId, @RequestParam(required = false) String msg) {
        audioAnalysisService.mockFail(analysisId, msg);
    }

    @PostMapping("/api/analysis/{analysisId:\\d+}/mock-segments")
    public void mockSegments(@PathVariable long analysisId) {
        audioAnalysisService.mockSegments(analysisId);
    }

    @PostMapping("/api/analysis/{analysisId:\\d+}/mock-run")
    public AudioAnalysisReportResponse mockRun(@PathVariable long analysisId) {
        return audioAnalysisService.mockRun(analysisId);
    }

    @PostMapping("/api/analysis/{analysisId:\\d+}/mock-run-async")
    public AudioAnalysisRunResponse mockRunAsync(@PathVariable long analysisId) {
        return audioAnalysisService.mockRunAsync(analysisId);
    }

    static class MockEndpointCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            boolean devProfile = environment.matchesProfiles("dev");
            boolean mockEnabled = Boolean.parseBoolean(environment.getProperty("ai.mock.enabled", "false"));
            return devProfile || mockEnabled;
        }
    }
}
