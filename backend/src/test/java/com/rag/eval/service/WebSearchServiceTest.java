package com.rag.eval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.eval.model.SearchResult;
import com.rag.eval.model.WebSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WebSearchServiceTest {

    private BochaSearchProvider bocha;
    private WebFetcher fetcher;
    private ConfigService config;
    private RedisTemplate<String, String> redis;
    private ValueOperations<String, String> valueOps;
    private WebSearchService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        bocha = mock(BochaSearchProvider.class);
        fetcher = mock(WebFetcher.class);
        config = mock(ConfigService.class);
        redis = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(config.getInt("web.search.max-results", 5)).thenReturn(5);
        when(config.getInt("web.top-k", 3)).thenReturn(3);
        when(config.getInt("web.chunk.max-chars", 2000)).thenReturn(2000);
        when(config.getInt("web.cache.ttl-seconds", 300)).thenReturn(300);
        service = new WebSearchService(bocha, fetcher, config, redis, new ObjectMapper());
    }

    @Test
    void cacheMiss_fetchesAndBuilds() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(bocha.search("q", 5)).thenReturn(List.of(new WebSearchResult("t1", "https://a", "snippet")));
        when(fetcher.fetchText("https://a")).thenReturn("page content");

        List<SearchResult> results = service.search("q");

        assertEquals(1, results.size());
        assertEquals("t1", results.get(0).getFileName());
        assertEquals("page content", results.get(0).getContent());
        assertEquals("web", results.get(0).getSource());
        verify(valueOps).set(anyString(), anyString(), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    void cacheHit_skipsProvider() {
        when(valueOps.get(anyString()))
            .thenReturn("[{\"chunkId\":\"u\",\"fileName\":\"t\",\"content\":\"c\",\"score\":0.9,\"source\":\"web\"}]");

        List<SearchResult> results = service.search("q");

        assertEquals(1, results.size());
        assertEquals("t", results.get(0).getFileName());
        verify(bocha, never()).search(anyString(), anyInt());
    }

    @Test
    void blankFetch_skipped() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(bocha.search("q", 5)).thenReturn(List.of(
            new WebSearchResult("t1", "https://a", "s"),
            new WebSearchResult("t2", "https://b", "s")));
        when(fetcher.fetchText("https://a")).thenReturn("  ");
        when(fetcher.fetchText("https://b")).thenReturn("valid");

        List<SearchResult> results = service.search("q");

        assertEquals(1, results.size());
        assertEquals("t2", results.get(0).getFileName());
    }

    @Test
    void topKLimit_enforced() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(bocha.search("q", 5)).thenReturn(List.of(
            new WebSearchResult("t1", "https://a", "s"),
            new WebSearchResult("t2", "https://b", "s"),
            new WebSearchResult("t3", "https://c", "s"),
            new WebSearchResult("t4", "https://d", "s")));
        when(fetcher.fetchText(anyString())).thenReturn("content");

        List<SearchResult> results = service.search("q");

        assertEquals(3, results.size());
    }
}
