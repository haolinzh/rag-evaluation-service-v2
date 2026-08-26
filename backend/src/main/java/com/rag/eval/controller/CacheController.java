package com.rag.eval.controller;

import com.rag.eval.service.SemanticCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final SemanticCacheService cacheService;

    public CacheController(SemanticCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @PreAuthorize("hasAuthority('cache:clear')")
    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clear() {
        cacheService.clear();
        return ResponseEntity.ok(Map.of("message", "Cache cleared"));
    }
}
