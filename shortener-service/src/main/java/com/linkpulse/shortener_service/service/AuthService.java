package com.linkpulse.shortener_service.service;

import java.time.Duration;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkpulse.shortener_service.dto.RegisterRequest;
import com.linkpulse.shortener_service.dto.RegisterResponse;
import com.linkpulse.shortener_service.entity.User;
import com.linkpulse.shortener_service.exception.EmailAlreadyRegisteredException;
import com.linkpulse.shortener_service.repository.UserRepository;
import com.linkpulse.shortener_service.dto.LoginRequest;
import com.linkpulse.shortener_service.dto.LoginResponse;
import com.linkpulse.shortener_service.exception.InvalidCredentialsException;
import com.linkpulse.shortener_service.exception.RateLimitExceededException;
import com.linkpulse.shortener_service.ratelimit.RateLimitResult;
import com.linkpulse.shortener_service.ratelimit.RateLimiter;
import com.linkpulse.shortener_service.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final RateLimiter rateLimiter;
    private final int loginLimit;
    private final Duration loginWindow;
    private final int registerLimit;
    private final Duration registerWindow;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RateLimiter rateLimiter,
                       @Value("${app.rate-limit.login.limit:10}") int loginLimit,
                       @Value("${app.rate-limit.login.window-seconds:60}") long loginWindowSeconds,
                       @Value("${app.rate-limit.register.limit:5}") int registerLimit,
                       @Value("${app.rate-limit.register.window-seconds:3600}") long registerWindowSeconds) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.loginLimit = loginLimit;
        this.loginWindow = Duration.ofSeconds(loginWindowSeconds);
        this.registerLimit = registerLimit;
        this.registerWindow = Duration.ofSeconds(registerWindowSeconds);
    }

    // per-IP because callers are anonymous here; no user id exists yet
    private void checkLimit(String bucket, String clientIp, int limit, Duration window) {
        RateLimitResult rate = rateLimiter.tryAcquire(bucket, "ip:" + clientIp, limit, window);
        if (!rate.allowed()) {
            throw new RateLimitExceededException(rate.retryAfterSeconds());
        }
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request, String clientIp) {
        checkLimit("register", clientIp, registerLimit, registerWindow);   // first: throttles before any DB or BCrypt work
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password())));
        return new RegisterResponse(user.getId(), user.getEmail(), user.getRole());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String clientIp) {
    checkLimit("login", clientIp, loginLimit, loginWindow);   // before BCrypt, so guessing can't burn CPU or go unthrottled
    String email = request.email().trim().toLowerCase();

    User user = userRepository.findByEmail(email)
            .orElseThrow(InvalidCredentialsException::new);

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
        throw new InvalidCredentialsException();
    }

    String token = jwtService.generateToken(user);
    return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }
}
