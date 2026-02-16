package com.wuhao.aiemotion.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "ai.mode", havingValue = "mock")
    public AiClient mockAiClient(AiMockDataFactory mockDataFactory) {
        return new MockAiClient(mockDataFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "ai.mode", havingValue = "spring", matchIfMissing = true)
    public AiClient springAiClient(ObjectMapper objectMapper,
                                   AiProperties properties,
                                   ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        return new SpringAiClient(objectMapper, properties, chatClientBuilderProvider);
    }
}
