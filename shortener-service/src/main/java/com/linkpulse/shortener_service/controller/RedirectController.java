package com.linkpulse.shortener_service.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.linkpulse.shortener_service.event.ClickEventPublisher;
import com.linkpulse.shortener_service.service.LinkService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class RedirectController {

    private final LinkService linkService;
    private final ClickEventPublisher clickEventPublisher;

    public RedirectController(LinkService linkService, ClickEventPublisher clickEventPublisher) {
        this.linkService = linkService;
        this.clickEventPublisher = clickEventPublisher;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        String longUrl = linkService.resolve(code);               // 404 / 410 throw here → no event

        clickEventPublisher.publish(code,                         // async: doesn't delay the 302
                request.getHeader(HttpHeaders.USER_AGENT),
                request.getHeader(HttpHeaders.REFERER));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }
}