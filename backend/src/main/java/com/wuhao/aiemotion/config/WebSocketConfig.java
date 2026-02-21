package com.wuhao.aiemotion.config;

import com.wuhao.aiemotion.websocket.TaskRealtimeWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TaskRealtimeWebSocketHandler taskRealtimeWebSocketHandler;

    @Value("${app.cors.allowed-origins:}")
    private String[] corsAllowedOrigins;

    @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private String[] corsAllowedOriginPatterns;

    public WebSocketConfig(TaskRealtimeWebSocketHandler taskRealtimeWebSocketHandler) {
        this.taskRealtimeWebSocketHandler = taskRealtimeWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry
                .addHandler(taskRealtimeWebSocketHandler, "/ws/tasks/stream");

        String[] patterns = sanitizeCorsValues(corsAllowedOriginPatterns);
        String[] origins = sanitizeCorsValues(corsAllowedOrigins);
        if (patterns.length > 0) {
            registration.setAllowedOriginPatterns(patterns);
        } else if (origins.length > 0) {
            registration.setAllowedOrigins(origins);
        } else {
            registration.setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
        }
    }

    private static String[] sanitizeCorsValues(String[] values) {
        if (values == null) {
            return new String[0];
        }
        return Arrays.stream(values)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .toArray(String[]::new);
    }
}
