package com.linkpulse.shortener_service.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;

public record UpdateExpiryRequest(@Future LocalDateTime expiresAt) { }   // null removes the expiry
