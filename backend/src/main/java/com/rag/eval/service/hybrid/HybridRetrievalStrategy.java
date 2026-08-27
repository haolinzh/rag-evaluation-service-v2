package com.rag.eval.service.hybrid;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Ported from the Spring AI Alibaba DataAgent {@code HybridRetrievalStrategy}. The result
 * carries per-leg counts/latencies so the caller can keep the retrieval metrics contract
 * intact.
 */
public interface HybridRetrievalStrategy {

    HybridRetrievalResult retrieve(HybridSearchRequest request);

    record HybridRetrievalResult(List<Document> documents, int keywordCount, int vectorCount,
                                 int overlapCount, long keywordLatencyMs, long vectorLatencyMs) {}
}
