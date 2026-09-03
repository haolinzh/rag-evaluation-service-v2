package com.rag.eval.controller;

import com.rag.eval.model.AppNotification;
import com.rag.eval.service.AuthService;
import com.rag.eval.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@PreAuthorize("hasAuthority('message:view')")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;

    public NotificationController(NotificationService notificationService, AuthService authService) {
        this.notificationService = notificationService;
        this.authService = authService;
    }

    @GetMapping
    public List<AppNotification> list(@RequestParam(defaultValue = "20") int limit) {
        return notificationService.listFor(authService.currentUser(), limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount(authService.currentUser()));
    }

    @PostMapping("/read")
    public Map<String, Boolean> markAllRead() {
        notificationService.markAllRead(authService.currentUser());
        return Map.of("ok", true);
    }
}
