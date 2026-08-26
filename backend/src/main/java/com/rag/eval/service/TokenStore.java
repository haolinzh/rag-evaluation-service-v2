package com.rag.eval.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class TokenStore {

    private static final String PREFIX = "auth:token:";
    private static final Duration TTL = Duration.ofHours(12);

    private final RedisTemplate<String, String> redis;

    public TokenStore(@Qualifier("redisTemplate") RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    public String create(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), TTL);
        return token;
    }

    public Long resolve(String token) {
        if (token == null || token.isBlank()) return null;
        String userId = redis.opsForValue().get(PREFIX + token);
        if (userId == null) return null;
        redis.expire(PREFIX + token, TTL);
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(PREFIX + token);
        }
    }
}
