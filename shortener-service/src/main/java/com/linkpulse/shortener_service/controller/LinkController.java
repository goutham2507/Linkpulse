// LinkController.java
package com.linkpulse.shortener_service.controller;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.linkpulse.shortener_service.dto.CreateLinkRequest;
import com.linkpulse.shortener_service.dto.CreateLinkResponse;
import com.linkpulse.shortener_service.service.LinkService;
@RestController
@RequestMapping("/v1/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<CreateLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.create(request));
    }
}