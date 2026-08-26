package com.rag.eval.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.eval.model.SearchResult;
import com.rag.eval.model.WebSearchResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class WebSearchService {

    private final BochaSearchProvider bochaSearchProvider;
    private final WebFetcher webFetcher;
    private final ConfigService config;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public WebSearchService(BochaSearchProvider bochaSearchProvider,
                            WebFetcher webFetcher,
                            ConfigService config,
                            @Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate,
                            ObjectMapper objectMapper) {
        this.bochaSearchProvider = bochaSearchProvider;
        this.webFetcher = webFetcher;
        this.config = config;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<SearchResult> search(String query) {
        String cacheKey = cacheKey(query);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return deserialize(cached);
        }

        int maxResults = config.getInt("web.search.max-results", 5);
        int topK = config.getInt("web.top-k", 3);
        int maxChars = config.getInt("web.chunk.max-chars", 2000);

        List<WebSearchResult> found = bochaSearchProvider.search(query, maxResults);
        List<SearchResult> results = new ArrayList<>();
        int rank = 0;
        for (WebSearchResult item : found) {
            if (results.size() >= topK) break;
            String text = webFetcher.fetchText(item.url());
            if (text == null || text.isBlank()) continue;
            String truncated = text.length() > maxChars ? text.substring(0, maxChars) : text;
            double score = Math.max(0.7, 0.9 - rank * 0.1);
            results.add(SearchResult.builder()
                .chunkId(item.url())
                .fileName(item.title())
                .content(truncated)
                .score(score)
                .source("web")
                .build());
            rank++;
        }

        redisTemplate.opsForValue().set(cacheKey, serialize(results),
            config.getInt("web.cache.ttl-seconds", 300), TimeUnit.SECONDS);
        return results;
    }

    private String cacheKey(String query) {
        return "cache:web:" + Integer.toHexString(query.toLowerCase().strip().hashCode());
    }

    private String serialize(List<SearchResult> results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<SearchResult> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<SearchResult>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
