package com.rag.eval.service.hybrid;

import com.rag.eval.service.ConfigService;
import com.rag.eval.service.DenseVectorStore;
import com.rag.eval.service.ElasticsearchService;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Hybrid retrieval: keyword leg via Elasticsearch {@code match(content)}, dense leg via
 * the configured {@link DenseVectorStore} backend. Ported from the DataAgent
 * {@code ElasticsearchHybridRetrievalStrategy}, adapted to the project's index schema and
 * pluggable dense backend.
 */
@Component
public class ElasticsearchHybridRetrievalStrategy extends AbstractHybridRetrievalStrategy {

    private final ElasticsearchService esService;
    private final Map<String, DenseVectorStore> denseStores;
    private final ConfigService config;

    public ElasticsearchHybridRetrievalStrategy(ElasticsearchService esService,
                                                Map<String, DenseVectorStore> denseStores,
                                                ConfigService config,
                                                FusionStrategy fusionStrategy) {
        super(fusionStrategy);
        this.esService = esService;
        this.denseStores = denseStores;
        this.config = config;
    }

    @Override
    protected List<Document> getDocumentsByKeywords(HybridSearchRequest request) {
        return esService.keywordSearch(request.query(), request.recallSize()).stream()
            .map(DocumentSupport::toDocument)
            .toList();
    }

    @Override
    protected List<Document> getDocumentsBySemantics(HybridSearchRequest request) {
        DenseVectorStore store = denseStores.get(config.get("vector.backend", "pgvector"));
        if (store == null) {
            store = denseStores.get("pgvector");
        }
        return store.searchByEmbedding(request.queryEmbedding(), request.recallSize(),
            request.similarityThreshold());
    }
}
