package com.wuhao.aiemotion.config;

import com.wuhao.aiemotion.websocket.TaskRealtimeWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TaskRealtimeWebSocketHandler taskRealtimeWebSocketHandler;

    @Value("${app.cors.allowed-origins:http://127.0.0.1:5173,http://localhost:5173}")
    private String[] corsAllowedOrigins;

    public WebSocketConfig(TaskRealtimeWebSocketHandler taskRealtimeWebSocketHandler) {
        this.taskRealtimeWebSocketHandler = taskRealtimeWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(taskRealtimeWebSocketHandler, "/ws/tasks/stream")
                .setAllowedOrigins(corsAllowedOrigins);
    }
}
