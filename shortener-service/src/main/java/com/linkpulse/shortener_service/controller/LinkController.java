// LinkController.java
package com.linkpulse.shortener_service.controller;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.linkpulse.shortener_service.dto.CreateLinkRequest;
import com.linkpulse.shortener_service.dto.CreateLinkResponse;
import com.linkpulse.shortener_service.service.LinkService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.linkpulse.shortener_service.dto.LinkSummary;
import com.linkpulse.shortener_service.dto.PageResponse;
import com.linkpulse.shortener_service.security.AuthUser;
@RestController
@RequestMapping("/v1/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<CreateLinkResponse> create(@Valid @RequestBody CreateLinkRequest request,
                                                    @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.create(request, user.id()));
    }

    @GetMapping
    public PageResponse<LinkSummary> myLinks(@AuthenticationPrincipal AuthUser user,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return linkService.listMine(user.id(), page, size);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code,
                                    @AuthenticationPrincipal AuthUser user) {
        linkService.delete(code, user.id());
        return ResponseEntity.noContent().build();      // 204
    }
}