package com.rag.eval.service.hybrid;

/**
 * Input to a hybrid retrieval run. {@code topK} is the fusion output size (for
 * {@code hybrid-rerank} that is the rerank candidate count, not the final answer count);
 * {@code queryEmbedding} is the pre-computed embedding string so the dense leg does not
 * re-embed.
 */
public record HybridSearchRequest(String query, int topK, int recallSize,
                                  double similarityThreshold, String queryEmbedding) {}
