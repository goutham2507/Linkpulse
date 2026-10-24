package com.linkpulse.shortener_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkpulse.shortener_service.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
