package com.rag.eval.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Dense (vector) search entry point used by the retrieval pipeline. Implementations
 * also implement Spring AI {@link org.springframework.ai.vectorstore.VectorStore}; this
 * narrower interface lets callers reuse a pre-computed query embedding instead of
 * re-embedding per call.
 */
public interface DenseVectorStore {

    List<Document> searchByEmbedding(String queryEmbedding, int topK, double threshold);
}
