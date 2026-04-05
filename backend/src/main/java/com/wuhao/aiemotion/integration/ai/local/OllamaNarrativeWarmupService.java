package com.wuhao.aiemotion.integration.ai.local;

import com.wuhao.aiemotion.service.AnalysisNarrativeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class OllamaNarrativeWarmupService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OllamaNarrativeWarmupService.class);

    private final AnalysisNarrativeProperties properties;
    private final OllamaNarrativeClient ollamaNarrativeClient;

    public OllamaNarrativeWarmupService(AnalysisNarrativeProperties properties,
                                        OllamaNarrativeClient ollamaNarrativeClient) {
        this.properties = properties;
        this.ollamaNarrativeClient = ollamaNarrativeClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!"ollama".equalsIgnoreCase(properties.getProvider())) {
            return;
        }
        if (!properties.getOllama().isPreload()) {
            return;
        }

        String model = properties.getOllama().getModel();
        String keepAlive = properties.getOllama().getKeepAlive();
        log.info("starting local Ollama narrative warmup: model={}, keepAlive={}", model, keepAlive);

        CompletableFuture.runAsync(() -> {
            try {
                ollamaNarrativeClient.preload();
                log.info("local Ollama narrative warmup completed: model={}", model);
            } catch (Exception ex) {
                log.warn("local Ollama narrative warmup skipped: model={}, reason={}", model, ex.getMessage());
            }
        });
    }
}
