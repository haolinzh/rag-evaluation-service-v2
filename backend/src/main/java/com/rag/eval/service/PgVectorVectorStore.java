package com.rag.eval.service;

import com.rag.eval.repository.VectorChunkRepo;
import com.rag.eval.service.hybrid.DocumentSupport;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("pgvector")
public class PgVectorVectorStore implements VectorStore, DenseVectorStore {

    private final VectorChunkRepo vectorChunkRepo;
    private final ConfigService config;
    private final DashScopeService dashScope;

    public PgVectorVectorStore(VectorChunkRepo vectorChunkRepo, ConfigService config, DashScopeService dashScope) {
        this.vectorChunkRepo = vectorChunkRepo;
        this.config = config;
        this.dashScope = dashScope;
    }

    @Override
    public List<Document> searchByEmbedding(String queryEmbedding, int topK, double threshold) {
        String indexType = config.get("vector.pgvector.index-type", "ivfflat");
        int probes = config.getInt("vector.pgvector.probes", 1);
        int efSearch = config.getInt("vector.pgvector.ef-search", 40);

        List<VectorChunkRepo.VectorSearchRow> rows =
            vectorChunkRepo.similaritySearch(queryEmbedding, threshold, topK, indexType, probes, efSearch);

        return rows.stream()
            .map(row -> DocumentSupport.document(
                row.chunkId(), row.content(), row.fileName(), row.chapter(), row.section(),
                Map.of(DocumentSupport.META_SEMANTIC_SCORE, row.similarity()), row.similarity()))
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
}
