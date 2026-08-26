package com.rag.eval.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class SemanticCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ConfigService config;

    public SemanticCacheService(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate,
                                 ConfigService config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    public String lookup(String normalizedQuestion, String mode, String model, String scope) {
        if (!config.getBool("cache.semantic.enabled", true)) return null;
        String key = cacheKey(normalizedQuestion, mode, model, scope);
        return redisTemplate.opsForValue().get(key);
    }

    public void store(String normalizedQuestion, String mode, String model, String answer, String scope) {
        if (!config.getBool("cache.semantic.enabled", true)) return;
        String key = cacheKey(normalizedQuestion, mode, model, scope);
        long ttlSeconds = config.getInt("cache.semantic.ttl-seconds", 3600);
        redisTemplate.opsForValue().set(key, answer, ttlSeconds, TimeUnit.SECONDS);
    }

    public void clear() {
        Set<String> keys = redisTemplate.keys("cache:qa:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String cacheKey(String question, String mode, String model, String scope) {
        // Keyed by retrieval mode, chat model AND user scope so answers/sources never
        // leak across users with different visibility.
        String normalized = question.toLowerCase().strip().replaceAll("\\s+", " ");
        return "cache:qa:" + Integer.toHexString((normalized + "|" + mode + "|" + model + "|" + scope).hashCode());
    }
}
