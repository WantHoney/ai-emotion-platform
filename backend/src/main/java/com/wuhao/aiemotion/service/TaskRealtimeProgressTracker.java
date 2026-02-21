package com.wuhao.aiemotion.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TaskRealtimeProgressTracker {

    private final ConcurrentHashMap<Long, ProgressState> stateMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicLong> sequenceMap = new ConcurrentHashMap<>();

    public void publish(long taskId, String phase, String message) {
        publish(taskId, phase, message, Map.of());
    }

    public void publish(long taskId, String phase, String message, Map<String, Object> details) {
        long sequence = sequenceMap.computeIfAbsent(taskId, key -> new AtomicLong(0)).incrementAndGet();
        stateMap.put(taskId, new ProgressState(
                phase,
                message,
                sequence,
                System.currentTimeMillis(),
                sanitize(details)
        ));
    }

    public Optional<ProgressState> current(long taskId) {
        return Optional.ofNullable(stateMap.get(taskId));
    }

    private Map<String, Object> sanitize(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                values.put(key, value);
            }
        });
        return values.isEmpty() ? Map.of() : Collections.unmodifiableMap(values);
    }

    public record ProgressState(
            String phase,
            String message,
            long sequence,
            long emittedAtMs,
            Map<String, Object> details
    ) {
    }
}
