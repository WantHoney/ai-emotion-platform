package com.wuhao.aiemotion.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ReportNoFormatter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String format(Long userRegisterNo, LocalDateTime createdAt, long userSequence) {
        long user = userRegisterNo == null ? 0L : Math.max(0L, userRegisterNo);
        LocalDateTime time = createdAt == null ? LocalDateTime.now() : createdAt;
        long serial = Math.max(0L, userSequence);
        return String.format("U%04d-%s-%04d", user, DATE_FMT.format(time), serial);
    }
}
