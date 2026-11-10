package com.linkpulse.shortener_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.linkpulse.shortener_service.dto.RegisterRequest;
import com.linkpulse.shortener_service.dto.RegisterResponse;
import com.linkpulse.shortener_service.service.AuthService;
import com.linkpulse.shortener_service.dto.LoginRequest;
import com.linkpulse.shortener_service.dto.LoginResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest http) {
        // behind Nginx (Day 44) use a trusted X-Forwarded-For instead, or every user shares the proxy IP
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, http.getRemoteAddr()));
    }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http) {
        // behind Nginx (Day 44) use a trusted X-Forwarded-For instead
        return ResponseEntity.ok(authService.login(request, http.getRemoteAddr()));
    }
}
