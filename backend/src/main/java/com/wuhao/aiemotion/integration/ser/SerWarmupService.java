package com.wuhao.aiemotion.integration.ser;

import com.wuhao.aiemotion.exception.SerServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Component
public class SerWarmupService {

    private static final Logger log = LoggerFactory.getLogger(SerWarmupService.class);

    private final SerClient serClient;
    private final SerProperties serProperties;

    public SerWarmupService(SerClient serClient, SerProperties serProperties) {
        this.serClient = serClient;
        this.serProperties = serProperties;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmupAfterStartup() {
        if (!serProperties.isEnabled()) {
            log.info("skip SER warmup because ser.enabled=false");
            return;
        }

        try {
            boolean warmed = serClient.warmup();
            if (warmed) {
                log.info("SER warmup success via health/warmup endpoint");
                return;
            }
            log.warn("SER warmup endpoints returned non-2xx, will rely on first task retry");
        } catch (SerServiceUnavailableException ex) {
            log.warn("SER warmup request failed: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("SER warmup failed unexpectedly", ex);
        }
    }
}
