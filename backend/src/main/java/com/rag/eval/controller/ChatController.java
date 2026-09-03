package com.rag.eval.controller;

import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.model.ChatMessage;
import com.rag.eval.model.ChatRequest;
import com.rag.eval.model.ChatResponse;
import com.rag.eval.service.AuthService;
import com.rag.eval.service.ChatService;
import com.rag.eval.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final AuthService authService;
    private final NotificationService notificationService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService, AuthService authService,
                          NotificationService notificationService) {
        this.chatService = chatService;
        this.authService = authService;
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        AuthenticatedUser viewer = authService.currentUserOrGuest();
        ChatResponse response = chatService.ask(request.getQuestion(), request.getSessionId(), request.getMode(),
            request.getWebSearch(), request.getChatMode(), viewer);
        notificationService.notify("chat", "问答", truncate(request.getQuestion()), viewer, null);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        AuthenticatedUser viewer = authService.currentUserOrGuest();
        notificationService.notify("chat", "问答", truncate(request.getQuestion()), viewer, null);
        executor.execute(() -> chatService.streamAsk(
            request.getQuestion(), request.getSessionId(), request.getMode(), request.getWebSearch(), request.getChatMode(), emitter, viewer));
        return emitter;
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatMessage>> history(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.getHistory(sessionId, authService.currentUser()));
    }

    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<Void> deleteHistory(@PathVariable String sessionId) {
        AuthenticatedUser viewer = authService.currentUser();
        chatService.deleteHistory(sessionId, viewer);
        notificationService.notify("chat", "删除会话", "删除会话 " + sessionId, viewer, null);
        return ResponseEntity.noContent().build();
    }

    private static String truncate(String q) {
        if (q == null) return "";
        return q.length() <= 50 ? q : q.substring(0, 50) + "…";
    }
}
