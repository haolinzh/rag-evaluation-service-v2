package com.rag.eval.service;

import com.rag.eval.service.hybrid.DocumentSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component("elasticsearch")
public class ElasticsearchVectorStore implements VectorStore, DenseVectorStore {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchVectorStore.class);

    private final ElasticsearchService esService;
    private final ConfigService config;
    private final DashScopeService dashScope;

    public ElasticsearchVectorStore(ElasticsearchService esService, ConfigService config, DashScopeService dashScope) {
        this.esService = esService;
        this.config = config;
        this.dashScope = dashScope;
    }

    @Override
    public List<Document> searchByEmbedding(String queryEmbedding, int topK, double threshold) {
        int numCandidates = config.getInt("vector.elasticsearch.num-candidates", 100);
        List<Double> vector = parseEmbedding(queryEmbedding);
        if (vector.isEmpty()) {
            log.warn("查询向量为空（embedding 可能失败），跳过 ES kNN 检索");
            return List.of();
        }
        return esService.knnSearch(vector, topK, numCandidates, threshold).stream()
            .map(DocumentSupport::toDocument)
            .toList();
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String embedding = DashScopeService.embeddingToString(dashScope.embed(request.getQuery()));
        return searchByEmbedding(embedding, request.getTopK(), request.getSimilarityThreshold());
    }

    @Override
    public void add(List<Document> documents) {
        throw new UnsupportedOperationException("Ingestion goes through IndexBuilder, not VectorStore.add");
    }

    @Override
    public void delete(List<String> idList) {
        throw new UnsupportedOperationException("Deletion goes through DocumentService, not VectorStore.delete");
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        throw new UnsupportedOperationException("Deletion goes through DocumentService, not VectorStore.delete");
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
