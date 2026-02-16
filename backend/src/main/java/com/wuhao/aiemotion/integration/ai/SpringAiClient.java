package com.wuhao.aiemotion.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.dto.EmotionAnalysisReport;
import com.wuhao.aiemotion.exception.UpstreamRateLimitException;
import com.wuhao.aiemotion.exception.UpstreamServiceException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpringAiClient implements AiClient {

    private final ObjectMapper objectMapper;
    private final AiProperties properties;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    public SpringAiClient(ObjectMapper objectMapper,
                          AiProperties properties,
                          ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.chatClientBuilderProvider = chatClientBuilderProvider;
    }

    @Override
    public AiAnalysisResult analyze(AiAnalysisRequest request) {
        AiAnalysisResult baseResult = requireConfiguredResult(request);
        Object reportJson = generateReportJson(baseResult, request);
        return baseResult.withReportJson(reportJson);
    }


    private AiAnalysisResult requireConfiguredResult(AiAnalysisRequest request) {
        if (chatClientBuilderProvider.getIfAvailable() == null) {
            throw new IllegalStateException("Spring AI ChatClient.Builder bean is missing. Check spring.ai.openai.enabled and dependencies.");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is missing or empty.");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("provider", "spring-ai-openrouter");
        summary.put("model", request.modelName());

        return new AiAnalysisResult(summary, List.of(), List.of(), null);
    }

    private Object generateReportJson(AiAnalysisResult baseResult, AiAnalysisRequest request) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(baseResult, request);

        ChatClient chatClient = buildChatClient();

        String content = callChatClient(chatClient, systemPrompt, userPrompt);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("No response content returned from Spring AI ChatClient");
        }
        return parseReportJsonStrict(content);
    }

    private ChatClient buildChatClient() {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new IllegalStateException("Spring AI ChatClient.Builder bean is missing. Check spring.ai.openai.enabled and dependencies.");
        }
        return builder.build();
    }

    private String callChatClient(ChatClient chatClient, String systemPrompt, String userPrompt) {
        try {
            Method promptMethod = chatClient.getClass().getMethod("prompt");
            Object promptSpec = promptMethod.invoke(chatClient);
            Object callSpec = promptSpec;
            try {
                Method systemMethod = promptSpec.getClass().getMethod("system", String.class);
                callSpec = systemMethod.invoke(promptSpec, systemPrompt);
            } catch (NoSuchMethodException ignore) {
                userPrompt = systemPrompt + "\n\n" + userPrompt;
            }
            Method userMethod = callSpec.getClass().getMethod("user", String.class);
            callSpec = userMethod.invoke(callSpec, userPrompt);
            Method callMethod = callSpec.getClass().getMethod("call");
            Object response = callMethod.invoke(callSpec);
            Method contentMethod = response.getClass().getMethod("content");
            Object content = contentMethod.invoke(response);
            return content == null ? null : content.toString();
        } catch (InvocationTargetException e) {
            throw mapUpstreamException(e.getTargetException());
        } catch (Exception e) {
            throw mapUpstreamException(e);
        }
    }

    private RuntimeException mapUpstreamException(Throwable throwable) {
        WebClientResponseException webClientError = findWebClientResponseException(throwable);
        if (webClientError != null) {
            int upstreamStatus = webClientError.getStatusCode().value();
            String responseBody = webClientError.getResponseBodyAsString();
            String bodyPart = (responseBody == null || responseBody.isBlank())
                    ? "<empty-body>"
                    : responseBody;
            if (upstreamStatus == 429) {
                int retryAfterSeconds = extractRetryAfterSeconds(webClientError);
                String message = "Upstream model is currently rate-limited. Please retry after "
                        + retryAfterSeconds
                        + " seconds. upstreamStatus="
                        + upstreamStatus
                        + ", upstreamBody="
                        + bodyPart;
                throw new UpstreamRateLimitException(message, throwable, upstreamStatus, bodyPart, retryAfterSeconds);
            }
            String message = "Spring AI upstream call failed. upstreamStatus="
                    + upstreamStatus
                    + ", upstreamStatusText="
                    + webClientError.getStatusText()
                    + ", upstreamBody="
                    + bodyPart;
            return new UpstreamServiceException(message, throwable, upstreamStatus, bodyPart);
        }
        return new IllegalStateException("Spring AI ChatClient call failed: " + throwable.getMessage(), throwable);
    }

    private int extractRetryAfterSeconds(WebClientResponseException webClientError) {
        String retryAfter = webClientError.getHeaders().getFirst("Retry-After");
        if (retryAfter == null || retryAfter.isBlank()) {
            return 30;
        }
        try {
            int seconds = Integer.parseInt(retryAfter.trim());
            return Math.max(seconds, 1);
        } catch (NumberFormatException ignore) {
            return 30;
        }
    }

    private WebClientResponseException findWebClientResponseException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof WebClientResponseException webClientResponseException) {
                return webClientResponseException;
            }
            current = current.getCause();
        }
        return null;
    }

    private EmotionAnalysisReport parseReportJsonStrict(String content) {
        try {
            return objectMapper.readValue(content, EmotionAnalysisReport.class);
        } catch (Exception e) {
            throw new IllegalStateException("LLM output is not valid RFC8259 JSON for EmotionAnalysisReport", e);
        }
    }

    private String buildSystemPrompt() {
        return "You are a JSON generator for emotion analysis reports. " +
                "Return ONLY RFC8259-compliant JSON with no extra text or markdown.";
    }

    private String buildUserPrompt(AiAnalysisResult baseResult, AiAnalysisRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("analysisId", request.analysisId());
        input.put("audioId", request.audioId());
        input.put("language", request.language());
        input.put("sampleRate", request.sampleRate());
        input.put("modelName", request.modelName());
        input.put("segments", baseResult.segments());
        input.put("overallEmotions", baseResult.overallEmotions());
        String inputJson;
        try {
            inputJson = objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            inputJson = input.toString();
        }

        return "Generate an emotion analysis report_json strictly matching this schema: " +
                "{\"overallEmotion\":string," +
                "\"confidence\":number," +
                "\"keyMoments\":[{\"startMs\":number,\"endMs\":number,\"text\":string," +
                "\"emotion\":string,\"score\":number}]," +
                "\"summary\":string}. " +
                "Use the provided segments/emotions to populate keyMoments and summary. " +
                "Input JSON: " + inputJson;
    }

}
