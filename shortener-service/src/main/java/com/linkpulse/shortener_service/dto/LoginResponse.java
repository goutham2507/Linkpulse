package com.linkpulse.shortener_service.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}