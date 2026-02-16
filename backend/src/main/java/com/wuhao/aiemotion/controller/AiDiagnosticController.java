package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.integration.ai.AiProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@ConditionalOnProperty(name = "ai.diag.enabled", havingValue = "true")
public class AiDiagnosticController {

    private final AiProperties aiProperties;
    private final Environment environment;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ObjectProvider<ChatModel> chatModelProvider;

    public AiDiagnosticController(AiProperties aiProperties,
                                  Environment environment,
                                  ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                                  ObjectProvider<ChatModel> chatModelProvider) {
        this.aiProperties = aiProperties;
        this.environment = environment;
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.chatModelProvider = chatModelProvider;
    }

    @GetMapping("/diag")
    public Map<String, Object> diag() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ai.mode", aiProperties.getMode());
        response.put("spring.ai.openai.enabled",
                environment.getProperty("spring.ai.openai.enabled", Boolean.class, true));
        response.put("chatClientPresent", chatClientBuilderProvider.getIfAvailable() != null);

        ChatModel chatModel = chatModelProvider.getIfAvailable();
        response.put("chatModelType", chatModel == null ? null : chatModel.getClass().getName());

        String resolvedModel = environment.getProperty("spring.ai.openai.chat.options.model");
        String resolvedBaseUrl = environment.getProperty("spring.ai.openai.base-url");
        response.put("resolvedModel", resolvedModel);
        response.put("resolvedBaseUrl", resolvedBaseUrl);
        response.put("resolvedChatCompletionsEndpoint", composeChatCompletionsEndpoint(resolvedBaseUrl));
        response.put("resolvedApiKey", mask(environment.getProperty("spring.ai.openai.api-key")));
        return response;
    }

    private String composeChatCompletionsEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/api")) {
            return normalized + "/v1/chat/completions";
        }
        if (normalized.endsWith("/api/v1")) {
            return normalized + "/chat/completions";
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
