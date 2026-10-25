package com.linkpulse.shortener_service.dto;

import java.time.LocalDateTime;

public record LinkSummary(
        String code,
        String shortUrl,
        String longUrl,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        boolean active) {
}