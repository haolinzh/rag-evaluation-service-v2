package com.rag.eval.service;

import com.rag.eval.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component("elasticsearch")
public class ElasticsearchVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchVectorStore.class);

    private final ElasticsearchService esService;
    private final ConfigService config;

    public ElasticsearchVectorStore(ElasticsearchService esService, ConfigService config) {
        this.esService = esService;
        this.config = config;
    }

    @Override
    public List<SearchResult> search(String queryEmbedding, int topK, double threshold) {
        int numCandidates = config.getInt("vector.elasticsearch.num-candidates", 100);
        List<Double> vector = parseEmbedding(queryEmbedding);
        if (vector.isEmpty()) {
            log.warn("查询向量为空（embedding 可能失败），跳过 ES kNN 检索");
            return List.of();
        }
        return esService.knnSearch(vector, topK, numCandidates, threshold);
    }

    @Override
    public String backend() {
        return "elasticsearch";
    }

    private List<Double> parseEmbedding(String s) {
        String trimmed = s == null ? "" : s.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isBlank()) return List.of();
        return Arrays.stream(trimmed.split(","))
            .map(String::trim)
            .filter(x -> !x.isEmpty())
            .map(Double::parseDouble)
            .toList();
    }
}
