package com.rag.eval.service;

import com.rag.eval.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryRewriteServiceTest {

    private static ChatMessage msg(String role, String content) {
        ChatMessage m = new ChatMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    @Test
    void emptyHistory_returnsOriginal() {
        QueryRewriteService svc = new QueryRewriteService(
            mock(DashScopeService.class), mock(ConfigService.class));
        var result = svc.rewrite("它和 RAG 比怎么样？", List.of());
        assertFalse(result.rewritten());
        assertEquals("它和 RAG 比怎么样？", result.query());
        assertEquals(0, result.promptTokens());
        assertEquals(0, result.completionTokens());
    }

    @Test
    void nullHistory_returnsOriginal() {
        QueryRewriteService svc = new QueryRewriteService(
            mock(DashScopeService.class), mock(ConfigService.class));
        var result = svc.rewrite("它和 RAG 比怎么样？", null);
        assertFalse(result.rewritten());
        assertEquals("它和 RAG 比怎么样？", result.query());
    }

    @Test
    void disabled_returnsOriginal() {
        ConfigService config = mock(ConfigService.class);
        when(config.getBool("retrieval.query-rewrite-enabled", true)).thenReturn(false);
        QueryRewriteService svc = new QueryRewriteService(mock(DashScopeService.class), config);
        var result = svc.rewrite("它是什么？", List.of(msg("user", "什么是 RAG")));
        assertFalse(result.rewritten());
        assertEquals("它是什么？", result.query());
    }

    @Test
    void withHistory_rewrites() {
        ConfigService config = mock(ConfigService.class);
        when(config.getBool("retrieval.query-rewrite-enabled", true)).thenReturn(true);
        when(config.get("dashscope.chat-model", "qwen-turbo")).thenReturn("qwen-turbo");
        DashScopeService dashScope = mock(DashScopeService.class);
        when(dashScope.chatWithModel(anyString(), anyDouble(), anyString(), anyString()))
            .thenReturn(new DashScopeService.ChatResult("RAG 与向量检索的区别", null, 50, 8));

        QueryRewriteService svc = new QueryRewriteService(dashScope, config);
        var history = List.of(msg("user", "什么是 RAG"), msg("assistant", "RAG 是检索增强生成"));
        var result = svc.rewrite("它和向量检索的区别是什么？", history);

        assertTrue(result.rewritten());
        assertEquals("RAG 与向量检索的区别", result.query());
        assertEquals(50, result.promptTokens());
        assertEquals(8, result.completionTokens());
    }

    @Test
    void rewriteSameAsQuestion_notRewritten() {
        ConfigService config = mock(ConfigService.class);
        when(config.getBool("retrieval.query-rewrite-enabled", true)).thenReturn(true);
        when(config.get("dashscope.chat-model", "qwen-turbo")).thenReturn("qwen-turbo");
        DashScopeService dashScope = mock(DashScopeService.class);
        when(dashScope.chatWithModel(anyString(), anyDouble(), anyString(), anyString()))
            .thenReturn(new DashScopeService.ChatResult("它是什么？", null, 20, 3));

        QueryRewriteService svc = new QueryRewriteService(dashScope, config);
        var result = svc.rewrite("它是什么？", List.of(msg("user", "什么是 RAG")));

        assertFalse(result.rewritten());
        assertEquals("它是什么？", result.query());
    }

    @Test
    void llmFailure_fallsBack() {
        ConfigService config = mock(ConfigService.class);
        when(config.getBool("retrieval.query-rewrite-enabled", true)).thenReturn(true);
        when(config.get("dashscope.chat-model", "qwen-turbo")).thenReturn("qwen-turbo");
        DashScopeService dashScope = mock(DashScopeService.class);
        when(dashScope.chatWithModel(anyString(), anyDouble(), anyString(), anyString()))
            .thenThrow(new RuntimeException("DashScope chat failed"));

        QueryRewriteService svc = new QueryRewriteService(dashScope, config);
        var result = svc.rewrite("它是什么？", List.of(msg("user", "什么是 RAG")));

        assertFalse(result.rewritten());
        assertEquals("它是什么？", result.query());
        assertEquals(0, result.promptTokens());
        assertEquals(0, result.completionTokens());
    }
}
