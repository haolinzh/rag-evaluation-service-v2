package com.rag.eval.service;

import com.rag.eval.model.SearchResult;

import java.util.List;

public interface VectorStore {

    List<SearchResult> search(String queryEmbedding, int topK, double threshold);

    String backend();
}
