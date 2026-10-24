package com.linkpulse.shortener_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email(message = "email must be a valid email address") @Size(max = 255)
        String email,

        @NotBlank @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        String password) {
}
