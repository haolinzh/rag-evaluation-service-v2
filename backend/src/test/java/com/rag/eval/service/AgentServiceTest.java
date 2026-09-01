package com.rag.eval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.eval.model.SearchResult;
import com.rag.eval.service.AgentService.AgentResult;
import com.rag.eval.service.AgentService.ToolCallEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    private DashScopeService dashScope;
    private RetrievalService retrievalService;
    private WebSearchService webSearchService;
    private ConfigService config;
    private AgentService agent;

    private List<ToolCallEvent> events;

    @BeforeEach
    void setUp() {
        dashScope = mock(DashScopeService.class);
        retrievalService = mock(RetrievalService.class);
        webSearchService = mock(WebSearchService.class);
        config = mock(ConfigService.class);
        when(config.getInt("agent.max-iterations", 5)).thenReturn(5);
        when(config.get("agent.model", "qwen-plus")).thenReturn("qwen-plus");
        when(config.getDouble("generation.temperature", 0.3)).thenReturn(0.3);
        when(config.getInt("agent.chunk-limit", 5)).thenReturn(5);
        agent = new AgentService(dashScope, retrievalService, webSearchService, config, new ObjectMapper());
        events = new ArrayList<>();
    }

    private static AssistantMessage toolCallMsg(String id, String name, String args) {
        return AssistantMessage.builder()
            .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, args)))
            .build();
    }

    private static ChatResponse response(AssistantMessage msg) {
        return new ChatResponse(List.of(new Generation(msg)));
    }

    private static SearchResult chunk(String fileName, String content) {
        return SearchResult.builder().fileName(fileName).content(content).build();
    }

    private static RetrievalService.RetrievalResult kbResult(List<SearchResult> results) {
        return new RetrievalService.RetrievalResult(results, List.of(), 0, results.size(), 0, 0, 0, 0, 0);
    }

    @Test
    void kbHit_thenAnswer() {
        when(dashScope.call(any()))
            .thenReturn(response(toolCallMsg("c1", "search_knowledge_base", "{\"query\":\"Spring AI\"}")))
            .thenReturn(response(new AssistantMessage("Spring AI 是一个框架。")));
        when(retrievalService.retrieve("Spring AI", "hybrid"))
            .thenReturn(kbResult(List.of(chunk("intro.pdf", "Spring AI ..."))));

        AgentResult r = agent.run("什么是 Spring AI？", "hybrid", true, events::add);

        assertEquals("Spring AI 是一个框架。", r.answer());
        assertEquals(2, r.llmCallCount());
        assertEquals(1, r.chunks().size());
        assertEquals(1, events.size());
        assertEquals("search_knowledge_base", events.get(0).tool());
        assertEquals("Spring AI", events.get(0).query());
    }

    @Test
    void kbEmpty_fallsBackToWeb() {
        when(dashScope.call(any()))
            .thenReturn(response(toolCallMsg("c1", "search_knowledge_base", "{\"query\":\"news\"}")))
            .thenReturn(response(toolCallMsg("c2", "search_web", "{\"query\":\"news\"}")))
            .thenReturn(response(new AssistantMessage("网上说...")));
        when(retrievalService.retrieve("news", "hybrid")).thenReturn(kbResult(List.of()));
        when(webSearchService.search("news")).thenReturn(List.of(chunk("web-title", "web content")));

        AgentResult r = agent.run("今天有什么新闻？", "hybrid", true, events::add);

        assertEquals("网上说...", r.answer());
        assertEquals(3, r.llmCallCount());
        assertEquals(1, r.chunks().size());
        assertEquals(2, events.size());
        assertEquals("search_web", events.get(1).tool());
    }

    @Test
    void webNotAllowed_returnsPermissionMessage() {
        when(dashScope.call(any()))
            .thenReturn(response(toolCallMsg("c1", "search_knowledge_base", "{\"query\":\"x\"}")))
            .thenReturn(response(toolCallMsg("c2", "search_web", "{\"query\":\"x\"}")))
            .thenReturn(response(new AssistantMessage("该知识库中暂无相关信息。")));
        when(retrievalService.retrieve("x", "hybrid")).thenReturn(kbResult(List.of()));

        AgentResult r = agent.run("x?", "hybrid", false, events::add);

        assertEquals("该知识库中暂无相关信息。", r.answer());
        verify(webSearchService, never()).search(anyString());
    }

    @Test
    void llmCallFailure_returnsError() {
        when(dashScope.call(any())).thenThrow(new RuntimeException("boom"));

        AgentResult r = agent.run("q", "hybrid", true, events::add);

        assertTrue(r.answer().contains("无法回答"));
        assertEquals(0, r.llmCallCount());
    }

    @Test
    void maxIterationsExceeded_returnsFallback() {
        when(config.getInt("agent.max-iterations", 5)).thenReturn(2);
        when(dashScope.call(any()))
            .thenReturn(response(toolCallMsg("c1", "search_knowledge_base", "{\"query\":\"x\"}")))
            .thenReturn(response(toolCallMsg("c2", "search_knowledge_base", "{\"query\":\"x\"}")));
        when(retrievalService.retrieve("x", "hybrid")).thenReturn(kbResult(List.of(chunk("a.pdf", "a"))));

        AgentResult r = agent.run("x?", "hybrid", true, events::add);

        assertTrue(r.answer().contains("无法回答"));
        assertEquals(2, r.llmCallCount());
    }
}
