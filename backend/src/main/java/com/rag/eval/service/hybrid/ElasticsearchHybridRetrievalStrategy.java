package com.rag.eval.service.hybrid;

import com.rag.eval.service.DenseVectorStoreLocator;
import com.rag.eval.service.ElasticsearchService;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hybrid retrieval: keyword leg via Elasticsearch {@code match(content)}, dense leg via
 * the configured {@link DenseVectorStoreLocator} backend. Ported from the DataAgent
 * {@code ElasticsearchHybridRetrievalStrategy}, adapted to the project's index schema and
 * pluggable dense backend.
 */
@Component
public class ElasticsearchHybridRetrievalStrategy extends AbstractHybridRetrievalStrategy {

    private final ElasticsearchService esService;
    private final DenseVectorStoreLocator vectorStoreLocator;

    public ElasticsearchHybridRetrievalStrategy(ElasticsearchService esService,
                                                DenseVectorStoreLocator vectorStoreLocator,
                                                FusionStrategy fusionStrategy) {
        super(fusionStrategy);
        this.esService = esService;
        this.vectorStoreLocator = vectorStoreLocator;
    }

    @Override
    protected List<Document> getDocumentsByKeywords(HybridSearchRequest request) {
        return esService.keywordSearch(request.query(), request.recallSize()).stream()
            .map(DocumentSupport::toDocument)
            .toList();
    }

    @Override
    protected List<Document> getDocumentsBySemantics(HybridSearchRequest request) {
        return vectorStoreLocator.resolve().searchByEmbedding(request.queryEmbedding(),
            request.recallSize(), request.similarityThreshold());
    }
}
