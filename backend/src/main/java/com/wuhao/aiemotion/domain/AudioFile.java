package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record AudioFile(
        Long id,
        Long userId,
        String originalName,
        String storedName,
        String storagePath,
        String contentType,
        Long sizeBytes,
        String sha256,
        Long durationMs,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
