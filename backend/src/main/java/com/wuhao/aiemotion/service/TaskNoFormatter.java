package com.wuhao.aiemotion.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class TaskNoFormatter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String format(Long ownerUserId, LocalDateTime createdAt, long taskId) {
        long user = ownerUserId == null ? 0L : Math.max(0L, ownerUserId);
        LocalDateTime time = createdAt == null ? LocalDateTime.now() : createdAt;
        return String.format("U%04d-%s-%04d", user, DATE_FMT.format(time), taskId);
    }
}

