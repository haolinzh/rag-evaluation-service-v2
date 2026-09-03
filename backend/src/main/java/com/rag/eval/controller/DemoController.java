package com.rag.eval.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.service.AuthService;
import com.rag.eval.service.DemoInitService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/demo")
@PreAuthorize("hasAuthority('config:edit')")
public class DemoController {

    private final DemoInitService demoInitService;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DemoController(DemoInitService demoInitService, ObjectMapper objectMapper, AuthService authService) {
        this.demoInitService = demoInitService;
        this.objectMapper = objectMapper;
        this.authService = authService;
    }

    @PostMapping(value = "/init", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter init() {
        SseEmitter emitter = new SseEmitter(0L);
        AuthenticatedUser viewer = authService.currentUser();
        executor.execute(() -> {
            try {
                demoInitService.init(event -> send(emitter, event), viewer);
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, Map<String, Object> event) {
        try {
            emitter.send(objectMapper.writeValueAsString(event));
        } catch (Exception ignored) {
            // client disconnected mid-stream
        }
    }
}
