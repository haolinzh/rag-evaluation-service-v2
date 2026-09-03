package com.rag.eval.controller;

import com.rag.eval.service.AuthService;
import com.rag.eval.service.NotificationService;
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
    private final AuthService authService;
    private final NotificationService notificationService;

    public CacheController(SemanticCacheService cacheService, AuthService authService,
                           NotificationService notificationService) {
        this.cacheService = cacheService;
        this.authService = authService;
        this.notificationService = notificationService;
    }

    @PreAuthorize("hasAuthority('cache:clear')")
    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clear() {
        cacheService.clear();
        notificationService.notify("cache", "清空语义缓存", "已清空语义缓存", authService.currentUser(), null);
        return ResponseEntity.ok(Map.of("message", "Cache cleared"));
    }
}
