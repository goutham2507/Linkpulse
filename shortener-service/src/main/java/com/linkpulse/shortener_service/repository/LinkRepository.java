package com.linkpulse.shortener_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkpulse.shortener_service.entity.Link;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByCode(String code);

    boolean existsByCode(String code);
}