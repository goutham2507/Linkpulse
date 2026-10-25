package com.linkpulse.shortener_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.linkpulse.shortener_service.service.LinkService;

@RestController
@RequestMapping("/v1/admin/links")
public class AdminLinkController {

    private final LinkService linkService;

    public AdminLinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PatchMapping("/{code}/disable")
    public ResponseEntity<Void> disable(@PathVariable String code) {
        linkService.adminDisable(code);
        return ResponseEntity.noContent().build();      // 204
    }

    @PatchMapping("/{code}/enable")
    public ResponseEntity<Void> enable(@PathVariable String code) {
        linkService.adminEnable(code);
        return ResponseEntity.noContent().build();      // 204
    }
}
