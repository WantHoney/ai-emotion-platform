package com.wuhao.aiemotion.integration.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AiStartupInfoLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiStartupInfoLogger.class);

    private final AiProperties aiProperties;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final Environment environment;

    public AiStartupInfoLogger(AiProperties aiProperties,
                               ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                               ObjectProvider<ChatModel> chatModelProvider,
                               Environment environment) {
        this.aiProperties = aiProperties;
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.chatModelProvider = chatModelProvider;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean chatClientPresent = chatClientBuilderProvider.getIfAvailable() != null;
        boolean openAiEnabled = environment.getProperty("spring.ai.openai.enabled", Boolean.class, true);
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        String chatModelType = chatModel == null ? "none" : chatModel.getClass().getSimpleName();
        log.info("AI startup mode='{}', spring.ai.openai.enabled={}, chatClientPresent={}, chatModelType={}",
                aiProperties.getMode(),
                openAiEnabled,
                chatClientPresent,
                chatModelType);
    }
}
