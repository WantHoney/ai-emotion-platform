package com.wuhao.aiemotion.integration.ai.local;

import com.wuhao.aiemotion.service.AnalysisNarrativeProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OllamaNarrativeClient {

    private final RestTemplateBuilder restTemplateBuilder;
    private final AnalysisNarrativeProperties properties;

    public OllamaNarrativeClient(RestTemplateBuilder restTemplateBuilder,
                                 AnalysisNarrativeProperties properties) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.properties = properties;
    }

    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, null);
    }

    public String chat(String systemPrompt, String userPrompt, Double temperatureOverride) {
        RestTemplate restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getOllama().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getOllama().getReadTimeoutMs()))
                .build();

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", temperatureOverride == null ? properties.getOllama().getTemperature() : temperatureOverride);

        OllamaChatRequest request = new OllamaChatRequest(
                properties.getOllama().getModel(),
                List.of(
                        new OllamaMessage("system", systemPrompt),
                        new OllamaMessage("user", userPrompt)
                ),
                false,
                "json",
                options,
                resolveKeepAlive()
        );

        ResponseEntity<OllamaChatResponse> response = restTemplate.postForEntity(
                normalizeUrl("/api/chat"),
                request,
                OllamaChatResponse.class
        );
        OllamaChatResponse body = response.getBody();
        if (body == null || body.message() == null || body.message().content() == null || body.message().content().isBlank()) {
            throw new IllegalStateException("Ollama returned an empty narrative response");
        }
        return body.message().content();
    }

    public void preload() {
        RestTemplate restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getOllama().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getOllama().getReadTimeoutMs()))
                .build();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getOllama().getModel());
        Object keepAlive = resolveKeepAlive();
        if (keepAlive != null) {
            request.put("keep_alive", keepAlive);
        }
        request.put("stream", false);

        restTemplate.postForEntity(
                normalizeUrl("/api/generate"),
                request,
                Map.class
        );
    }

    private Object resolveKeepAlive() {
        String keepAlive = properties.getOllama().getKeepAlive();
        if (keepAlive == null || keepAlive.isBlank()) {
            return null;
        }
        return keepAlive.trim();
    }

    private String normalizeUrl(String path) {
        String baseUrl = properties.getOllama().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("analysis.narrative.ollama.base-url is blank");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private record OllamaChatRequest(
            String model,
            List<OllamaMessage> messages,
            boolean stream,
            Object format,
            Map<String, Object> options,
            Object keep_alive
    ) {
    }

    private record OllamaChatResponse(
            String model,
            OllamaMessage message,
            boolean done
    ) {
    }

    private record OllamaMessage(
            String role,
            String content
    ) {
    }
}
