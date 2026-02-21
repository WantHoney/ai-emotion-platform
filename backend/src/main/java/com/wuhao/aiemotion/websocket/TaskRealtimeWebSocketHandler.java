package com.wuhao.aiemotion.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.dto.response.TaskRealtimeSnapshotResponse;
import com.wuhao.aiemotion.service.AuthService;
import com.wuhao.aiemotion.service.TaskRealtimeSnapshotService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class TaskRealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TaskRealtimeWebSocketHandler.class);
    private static final CloseStatus UNAUTHORIZED_CLOSE = new CloseStatus(4401, "Unauthorized");
    private static final CloseStatus FORBIDDEN_CLOSE = new CloseStatus(4403, "Forbidden");
    private static final CloseStatus BAD_REQUEST_CLOSE = new CloseStatus(4400, "Bad Request");
    private static final CloseStatus INTERNAL_CLOSE = new CloseStatus(4500, "Internal Error");

    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final TaskRealtimeSnapshotService snapshotService;
    private final ScheduledExecutorService pushExecutor;
    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    @Value("${analysis.realtime.push-interval-ms:1000}")
    private long pushIntervalMs;

    public TaskRealtimeWebSocketHandler(ObjectMapper objectMapper,
                                        AuthService authService,
                                        TaskRealtimeSnapshotService snapshotService) {
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.snapshotService = snapshotService;
        this.pushExecutor = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "task-realtime-ws-push");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SessionState state;
        try {
            state = authorize(session);
        } catch (ResponseStatusException ex) {
            closeByStatus(session, ex);
            return;
        } catch (IllegalArgumentException ex) {
            closeQuietly(session, BAD_REQUEST_CLOSE.withReason(safeReason(ex.getMessage())));
            return;
        }

        sessions.put(session.getId(), state);
        pushSnapshot(state, true);

        ScheduledFuture<?> future = pushExecutor.scheduleWithFixedDelay(
                () -> pushSnapshot(state, false),
                pushIntervalMs,
                pushIntervalMs,
                TimeUnit.MILLISECONDS
        );
        state.setFuture(future);

        log.info("task realtime websocket connected: sessionId={}, taskId={}, userId={}",
                session.getId(), state.taskId(), state.user().userId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        release(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("task realtime websocket transport error: sessionId={}, reason={}",
                session.getId(), exception.getMessage());
        release(session.getId());
        closeQuietly(session, INTERNAL_CLOSE);
    }

    @PreDestroy
    public void shutdown() {
        pushExecutor.shutdownNow();
    }

    private SessionState authorize(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            throw new IllegalArgumentException("missing websocket uri");
        }

        Map<String, String> query = parseQuery(uri);
        String taskIdRaw = firstNonBlank(query.get("taskId"), query.get("task_id"));
        if (taskIdRaw == null) {
            throw new IllegalArgumentException("missing taskId");
        }

        long taskId;
        try {
            taskId = Long.parseLong(taskIdRaw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid taskId");
        }
        if (taskId <= 0) {
            throw new IllegalArgumentException("invalid taskId");
        }

        String token = firstNonBlank(
                query.get("accessToken"),
                query.get("token"),
                parseBearerToken(session.getHandshakeHeaders().getFirst(HttpHeaders.AUTHORIZATION)),
                parseTokenFromCookieHeader(session.getHandshakeHeaders().getFirst(HttpHeaders.COOKIE))
        );
        if (token == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "missing access token");
        }
        AuthService.UserProfile user = authService.requireValidUser(token);
        return new SessionState(session, taskId, user);
    }

    private void pushSnapshot(SessionState state, boolean force) {
        if (!state.session().isOpen()) {
            release(state.session().getId());
            return;
        }
        try {
            TaskRealtimeSnapshotResponse snapshot = snapshotService.buildSnapshot(state.taskId(), state.user());
            String payload = objectMapper.writeValueAsString(snapshot);
            if (!force && payload.equals(state.lastPayload())) {
                if (snapshot.terminal()) {
                    closeQuietly(state.session(), CloseStatus.NORMAL);
                    release(state.session().getId());
                }
                return;
            }
            synchronized (state.session()) {
                if (!state.session().isOpen()) {
                    release(state.session().getId());
                    return;
                }
                state.session().sendMessage(new TextMessage(payload));
                state.setLastPayload(payload);
            }
            if (snapshot.terminal()) {
                closeQuietly(state.session(), CloseStatus.NORMAL);
                release(state.session().getId());
            }
        } catch (ResponseStatusException ex) {
            closeByStatus(state.session(), ex);
            release(state.session().getId());
        } catch (Exception ex) {
            log.warn("task realtime websocket push failed: sessionId={}, taskId={}, reason={}",
                    state.session().getId(), state.taskId(), ex.getMessage());
            closeQuietly(state.session(), INTERNAL_CLOSE);
            release(state.session().getId());
        }
    }

    private void release(String sessionId) {
        SessionState state = sessions.remove(sessionId);
        if (state == null) {
            return;
        }
        ScheduledFuture<?> future = state.future();
        if (future != null) {
            future.cancel(false);
        }
    }

    private void closeByStatus(WebSocketSession session, ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        if (code == 401) {
            closeQuietly(session, UNAUTHORIZED_CLOSE.withReason(safeReason(ex.getReason())));
            return;
        }
        if (code == 403) {
            closeQuietly(session, FORBIDDEN_CLOSE.withReason(safeReason(ex.getReason())));
            return;
        }
        if (code == 400 || code == 404) {
            closeQuietly(session, BAD_REQUEST_CLOSE.withReason(safeReason(ex.getReason())));
            return;
        }
        closeQuietly(session, INTERNAL_CLOSE.withReason(safeReason(ex.getReason())));
    }

    private void closeQuietly(WebSocketSession session, CloseStatus closeStatus) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(closeStatus);
        } catch (IOException ignore) {
            // ignore close error
        }
    }

    private String parseBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.length() >= 7 && trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private String parseTokenFromCookieHeader(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }
        String[] parts = cookieHeader.split(";");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String name = kv[0].trim();
            if (!"accessToken".equals(name) && !"access_token".equals(name)) {
                continue;
            }
            String value = kv[1].trim();
            return parseBearerToken(value);
        }
        return null;
    }

    private Map<String, String> parseQuery(URI uri) {
        String raw = UriComponentsBuilder.fromUri(uri).build().getQuery();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        String[] items = raw.split("&");
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String[] kv = item.split("=", 2);
            String key = decode(kv[0]);
            String value = kv.length > 1 ? decode(kv[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "no_reason";
        }
        String sanitized = reason.replaceAll("[\\r\\n\\t]", " ").trim();
        return sanitized.length() > 80 ? sanitized.substring(0, 80) : sanitized;
    }

    private static final class SessionState {
        private final WebSocketSession session;
        private final long taskId;
        private final AuthService.UserProfile user;
        private volatile ScheduledFuture<?> future;
        private volatile String lastPayload;

        private SessionState(WebSocketSession session, long taskId, AuthService.UserProfile user) {
            this.session = session;
            this.taskId = taskId;
            this.user = user;
        }

        private WebSocketSession session() {
            return session;
        }

        private long taskId() {
            return taskId;
        }

        private AuthService.UserProfile user() {
            return user;
        }

        private ScheduledFuture<?> future() {
            return future;
        }

        private void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        private String lastPayload() {
            return lastPayload;
        }

        private void setLastPayload(String lastPayload) {
            this.lastPayload = lastPayload;
        }
    }
}
