package com.wuhao.aiemotion.integration.ai;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenRouterIntegrationTest {

    @Test
    void shouldCallOpenRouterViaSpringAiWhenApiKeyPresent() {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "OPENROUTER_API_KEY is required to run this integration test");

        String baseUrl = System.getenv().getOrDefault("OPENROUTER_BASE_URL", "https://openrouter.ai/api");
        String model = System.getenv().getOrDefault("OPENROUTER_MODEL", "openrouter/free");

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi,
                OpenAiChatOptions.builder().model(model).build());

        ChatClient chatClient = ChatClient.builder(chatModel).build();
        String content = chatClient.prompt()
                .user("Reply with a single short sentence that includes the word 'ready'.")
                .call()
                .content();

        assertNotNull(content);
        assertFalse(content.isBlank());
    }
}
