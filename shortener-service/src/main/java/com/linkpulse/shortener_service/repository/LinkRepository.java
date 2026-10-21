package com.linkpulse.shortener_service.repository;
import com.linkpulse.shortener_service.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByCode(String code);   // this is the redirect lookup
}