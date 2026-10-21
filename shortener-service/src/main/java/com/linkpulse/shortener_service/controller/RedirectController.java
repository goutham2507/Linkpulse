package com.linkpulse.shortener_service.controller;

import com.linkpulse.shortener_service.service.LinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final LinkService linkService;

    public RedirectController(LinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String longUrl = linkService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)       // 302
                .location(URI.create(longUrl))               // Location header
                .build();
    }
}