package com.wuhao.aiemotion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Map;

/**
 * AI 情感分析后端主应用。
 *
 * 说明：默认启用 OpenAI/Spring AI 自动配置；当 ai.mode=mock 或 ai.mock.enabled=true 时，
 * 在启动早期动态关闭 spring.ai.openai.enabled，避免 mock 场景对真实 LLM Bean 的依赖。
 */
@EnableAsync
@SpringBootApplication
public class AiEmotionBackendApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AiEmotionBackendApplication.class);
        application.addInitializers(context -> {
            ConfigurableEnvironment env = context.getEnvironment();
            String aiMode = env.getProperty("ai.mode", env.getProperty("AI_MODE", "spring"));
            boolean mockEnabled = Boolean.parseBoolean(env.getProperty("ai.mock.enabled", "false"));
            String springAiApiKey = env.getProperty("spring.ai.openai.api-key", "");
            String openRouterApiKey = env.getProperty("OPENROUTER_API_KEY", "");
            boolean apiKeyMissing = (springAiApiKey == null || springAiApiKey.isBlank())
                    && (openRouterApiKey == null || openRouterApiKey.isBlank());
            if ("mock".equalsIgnoreCase(aiMode) || mockEnabled || apiKeyMissing) {
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("aiModeOverrides", Map.of(
                                "spring.ai.openai.enabled", "false",
                                "spring.autoconfigure.exclude",
                                "org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration,"
                                        + "org.springframework.ai.autoconfigure.chat.client.ChatClientAutoConfiguration"
                        ))
                );
            }
        });
        application.run(args);
    }
}
