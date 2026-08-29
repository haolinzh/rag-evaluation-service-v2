package com.rag.eval.service;

import com.rag.eval.model.SearchResult;
import com.rag.eval.service.hybrid.DocumentSupport;
import com.rag.eval.service.hybrid.HybridRetrievalStrategy;
import com.rag.eval.service.hybrid.HybridSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.entries;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final DenseVectorStoreLocator vectorStoreLocator;
    private final HybridRetrievalStrategy hybridStrategy;
    private final RerankService rerankService;
    private final DashScopeService dashScope;
    private final ConfigService config;

    public RetrievalService(DenseVectorStoreLocator vectorStoreLocator,
                            HybridRetrievalStrategy hybridStrategy,
                            RerankService rerankService,
                            DashScopeService dashScope,
                            ConfigService config) {
        this.vectorStoreLocator = vectorStoreLocator;
        this.hybridStrategy = hybridStrategy;
        this.rerankService = rerankService;
        this.dashScope = dashScope;
        this.config = config;
    }

    public RetrievalResult retrieve(String query, String requestedMode) {
        String effectiveMode = resolveMode(requestedMode);
        int topK = config.getInt("retrieval.top-k", 5);
        int recallMultiplier = config.getInt("retrieval.recall-size-multiplier", 3);
        int rerankCandidates = config.getInt("retrieval.rerank-candidates", 20);

        Instant embStart = Instant.now();
        String queryEmb = embedQuery(query);
        long embeddingLatencyMs = Duration.between(embStart, Instant.now()).toMillis();

        if ("vector".equals(effectiveMode)) {
            Instant vectorStart = Instant.now();
            List<Document> docs = vectorStoreLocator.resolve().searchByEmbedding(queryEmb, topK, similarityThreshold());
            long vectorLatencyMs = Duration.between(vectorStart, Instant.now()).toMillis();
            List<SearchResult> results = docs.stream()
                .map(d -> DocumentSupport.toSearchResult(d, "semantic"))
                .toList();
            logRetrieval(effectiveMode, 0, results.size(), 0, embeddingLatencyMs, 0, vectorLatencyMs, 0, results);
            return new RetrievalResult(results, List.of(), 0, results.size(), 0,
                embeddingLatencyMs, 0, vectorLatencyMs, 0);
        }

        // Hybrid + hybrid-rerank share the parallel keyword + semantic recall
        int recallSize = Math.max(topK * recallMultiplier, 30);
        int fusionTopK = "hybrid-rerank".equals(effectiveMode) ? rerankCandidates : topK;

        HybridSearchRequest request =
            new HybridSearchRequest(query, fusionTopK, recallSize, similarityThreshold(), queryEmb);
        HybridRetrievalStrategy.HybridRetrievalResult hybrid = hybridStrategy.retrieve(request);

        List<SearchResult> fused = hybrid.documents().stream()
            .map(d -> DocumentSupport.toSearchResult(d, "rrf"))
            .toList();

        if ("hybrid-rerank".equals(effectiveMode)) {
            Instant rerankStart = Instant.now();
            List<SearchResult> reranked = rerankService.rerank(query, fused, topK);
            long rerankLatency = Duration.between(rerankStart, Instant.now()).toMillis();
            logRetrieval(effectiveMode, hybrid.keywordCount(), hybrid.vectorCount(), hybrid.overlapCount(),
                embeddingLatencyMs, hybrid.keywordLatencyMs(), hybrid.vectorLatencyMs(), rerankLatency, reranked);
            return new RetrievalResult(reranked, fused, hybrid.keywordCount(), hybrid.vectorCount(),
                hybrid.overlapCount(), embeddingLatencyMs, hybrid.keywordLatencyMs(),
                hybrid.vectorLatencyMs(), rerankLatency);
        }

        logRetrieval(effectiveMode, hybrid.keywordCount(), hybrid.vectorCount(), hybrid.overlapCount(),
            embeddingLatencyMs, hybrid.keywordLatencyMs(), hybrid.vectorLatencyMs(), 0, fused);
        return new RetrievalResult(fused, List.of(), hybrid.keywordCount(), hybrid.vectorCount(),
            hybrid.overlapCount(), embeddingLatencyMs, hybrid.keywordLatencyMs(),
            hybrid.vectorLatencyMs(), 0);
    }

    public record RetrievalResult(List<SearchResult> results,
                                  List<SearchResult> rerankCandidates,
                                  int keywordCount, int vectorCount, int overlapCount,
                                  long embeddingLatencyMs, long keywordLatencyMs,
                                  long vectorLatencyMs, long rerankLatencyMs) {}

    private void logRetrieval(String mode, int keywordCount, int vectorCount, int overlap,
                              long embeddingLatencyMs, long keywordLatencyMs, long vectorLatencyMs,
                              long rerankLatencyMs, List<SearchResult> results) {
        double maxScore = results.stream().mapToDouble(SearchResult::getScore).max().orElse(0.0);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("event", "retrieval");
        fields.put("mode", mode);
        fields.put("keyword_count", keywordCount);
        fields.put("vector_count", vectorCount);
        fields.put("overlap_count", overlap);
        fields.put("embedding_latency_ms", embeddingLatencyMs);
        fields.put("keyword_latency_ms", keywordLatencyMs);
        fields.put("vector_latency_ms", vectorLatencyMs);
        fields.put("rerank_latency_ms", rerankLatencyMs);
        fields.put("chunks_retrieved", results.size());
        fields.put("max_chunk_score", maxScore);
        log.info("Retrieval completed {}", entries(fields));
    }

    public String resolveMode(String requestedMode) {
        if ("rerank".equalsIgnoreCase(requestedMode)) {
            return "hybrid-rerank";
        }
        if ("vector".equalsIgnoreCase(requestedMode)
                || "hybrid".equalsIgnoreCase(requestedMode)
                || "hybrid-rerank".equalsIgnoreCase(requestedMode)) {
            return requestedMode.toLowerCase();
        }
        return config.get("retrieval.mode", "hybrid");
    }

    public String getMode() {
        return config.get("retrieval.mode", "hybrid");
    }

    private String embedQuery(String query) {
        List<Double> embedding = dashScope.embed(query);
        return DashScopeService.embeddingToString(embedding);
    }

    private double similarityThreshold() {
        return config.getDouble("retrieval.similarity-threshold", 0.4);
    }
}
