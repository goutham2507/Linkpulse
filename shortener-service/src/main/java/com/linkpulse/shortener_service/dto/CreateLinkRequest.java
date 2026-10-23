package com.linkpulse.shortener_service.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(
        @NotBlank @Size(max = 2048) String longUrl,

        @Pattern(regexp = "^[a-zA-Z0-9_-]{4,20}$",
                 message = "alias must be 4-20 chars: letters, digits, _ or -")
        String alias,

        @Future(message = "expiresAt must be in the future")
        LocalDateTime expiresAt) {
}