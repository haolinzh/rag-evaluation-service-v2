package com.rag.eval.controller;

import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.model.RequestLog;
import com.rag.eval.repository.RequestLogRepo;
import com.rag.eval.service.AuthService;
import com.rag.eval.service.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final RequestLogRepo requestLogRepo;
    private final AuthService authService;
    private final NotificationService notificationService;

    public LogController(RequestLogRepo requestLogRepo, AuthService authService,
                         NotificationService notificationService) {
        this.requestLogRepo = requestLogRepo;
        this.authService = authService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<RequestLog> list(@RequestParam(defaultValue = "100") int limit) {
        int size = Math.max(1, Math.min(limit, 1000));
        PageRequest page = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "id"));
        AuthenticatedUser viewer = authService.currentUser();
        if (viewer != null && viewer.permissions().contains("log:view")) {
            return requestLogRepo.findAll(page).getContent();
        }
        if (viewer == null) {
            return List.of();
        }
        return requestLogRepo.findByOwnerIdOrderByIdDesc(viewer.id(), page);
    }

    @PreAuthorize("hasAuthority('log:clear')")
    @DeleteMapping
    public ResponseEntity<Map<String, String>> clear() {
        requestLogRepo.deleteAll();
        notificationService.notify("log", "清空请求日志", "已清空请求日志", authService.currentUser(), null);
        return ResponseEntity.ok(Map.of("message", "Logs cleared"));
    }
}
