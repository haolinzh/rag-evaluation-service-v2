package com.rag.eval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.eval.model.WebSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BochaSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(BochaSearchProvider.class);

    private final ConfigService config;
    private final ObjectMapper objectMapper;

    public BochaSearchProvider(ConfigService config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public List<WebSearchResult> search(String query, int maxResults) {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            log.warn("Bocha 联网搜索跳过：未配置 web.search.api-key");
            return List.of();
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "query", query,
                "count", maxResults,
                "summary", true));
            long timeoutMs = config.getInt("web.fetch.timeout-ms", 10000);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.bochaai.com/v1/web-search"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Bocha 搜索失败 HTTP {}: {}", response.statusCode(), response.body());
                return List.of();
            }
            return parse(response.body());
        } catch (Exception e) {
            log.warn("Bocha 搜索异常: {}", e.getMessage());
            return List.of();
        }
    }

    private String resolveApiKey() {
        String key = config.get("web.search.api-key", "");
        return key == null ? "" : key.trim();
    }

    private List<WebSearchResult> parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode value = root.path("data").path("webPages").path("value");
        List<WebSearchResult> results = new ArrayList<>();
        if (value.isArray()) {
            for (JsonNode item : value) {
                String name = item.path("name").asText("");
                String url = item.path("url").asText("");
                String snippet = item.path("summary").asText("");
                if (snippet.isBlank()) {
                    snippet = item.path("snippet").asText("");
                }
                if (!url.isBlank()) {
                    results.add(new WebSearchResult(name, url, snippet));
                }
            }
        }
        return results;
    }
}
