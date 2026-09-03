package com.rag.eval.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.model.EvaluationQuestion;
import com.rag.eval.model.EvaluationReport;
import com.rag.eval.model.EvaluationRequest;
import com.rag.eval.model.EvaluationRunMeta;
import com.rag.eval.model.JudgeConfig;
import com.rag.eval.service.AuthService;
import com.rag.eval.service.EvaluationQuestionService;
import com.rag.eval.service.EvaluationService;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/evaluation")
@PreAuthorize("hasAuthority('evaluation:use')")
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final EvaluationQuestionService questionService;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public EvaluationController(EvaluationService evaluationService,
                                EvaluationQuestionService questionService,
                                ObjectMapper objectMapper,
                                AuthService authService) {
        this.evaluationService = evaluationService;
        this.questionService = questionService;
        this.objectMapper = objectMapper;
        this.authService = authService;
    }

    @GetMapping("/questions")
    public List<EvaluationQuestion> questions() {
        return questionService.list();
    }

    @PostMapping("/questions")
    public ResponseEntity<?> createQuestion(@RequestBody EvaluationQuestion question) {
        try {
            return ResponseEntity.ok(questionService.create(question));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<?> updateQuestion(@PathVariable String id, @RequestBody EvaluationQuestion question) {
        try {
            return ResponseEntity.ok(questionService.update(id, question));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable String id) {
        try {
            questionService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public List<EvaluationRunMeta> history() {
        return evaluationService.listRuns();
    }

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("running", evaluationService.isRunning());
    }

    @PostMapping("/cancel")
    public Map<String, Boolean> cancel() {
        evaluationService.cancel();
        return Map.of("cancelled", true);
    }

    @GetMapping("/history/{id}")
    public EvaluationReport historyDetail(@PathVariable Long id) {
        return evaluationService.getRun(id);
    }

    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@RequestBody(required = false) EvaluationRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        List<String> modes = request == null ? null : request.getModes();
        boolean clearCache = request == null || request.isClearCache();
        List<String> types = request == null ? null : request.getTypes();
        JudgeConfig judge = request == null ? new JudgeConfig(null, null)
            : new JudgeConfig(request.getJudgeEnabled(), request.getJudgeModel());
        AuthenticatedUser viewer = authService.currentUser();
        String traceId = MDC.get("traceId");
        executor.execute(() -> {
            if (traceId != null) MDC.put("traceId", traceId);
            try {
                evaluationService.runEvaluation(modes, clearCache, judge, types, event -> send(emitter, event), viewer);
            } finally {
                MDC.remove("traceId");
                emitter.complete();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, Map<String, Object> event) {
        try {
            emitter.send(objectMapper.writeValueAsString(event));
        } catch (Exception ignored) {
            // client disconnected mid-run
        }
    }
}
