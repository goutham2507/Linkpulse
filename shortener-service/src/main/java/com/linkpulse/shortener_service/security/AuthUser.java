package com.linkpulse.shortener_service.security;

public record AuthUser(Long id, String email, String role) {
}
