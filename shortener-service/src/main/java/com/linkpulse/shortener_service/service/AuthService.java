package com.linkpulse.shortener_service.service;

import java.util.Locale;

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
import com.linkpulse.shortener_service.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password())));
        return new RegisterResponse(user.getId(), user.getEmail(), user.getRole());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
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
