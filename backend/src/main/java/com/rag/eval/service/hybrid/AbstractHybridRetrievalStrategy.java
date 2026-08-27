package com.rag.eval.service.hybrid;

import org.springframework.ai.document.Document;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Template for hybrid retrieval: run the keyword (sparse) and semantic (dense) legs in
 * parallel, then fuse with a {@link FusionStrategy}. Ported from the DataAgent
 * {@code AbstractHybridRetrievalStrategy}.
 */
public abstract class AbstractHybridRetrievalStrategy implements HybridRetrievalStrategy {

    protected final FusionStrategy fusionStrategy;

    protected AbstractHybridRetrievalStrategy(FusionStrategy fusionStrategy) {
        this.fusionStrategy = fusionStrategy;
    }

    @Override
    public HybridRetrievalResult retrieve(HybridSearchRequest request) {
        AtomicLong keywordLatency = new AtomicLong();
        AtomicLong vectorLatency = new AtomicLong();

        CompletableFuture<List<Document>> keywordFuture = CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            List<Document> r = getDocumentsByKeywords(request);
            keywordLatency.set(System.currentTimeMillis() - start);
            return r;
        });
        CompletableFuture<List<Document>> vectorFuture = CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            List<Document> r = getDocumentsBySemantics(request);
            vectorLatency.set(System.currentTimeMillis() - start);
            return r;
        });

        List<Document> keywordResults = keywordFuture.join();
        List<Document> vectorResults = vectorFuture.join();
        int overlap = overlapCount(keywordResults, vectorResults);

        List<Document> fused = fusionStrategy.fuseResults(request.topK(), keywordResults, vectorResults);
        return new HybridRetrievalResult(fused, keywordResults.size(), vectorResults.size(), overlap,
            keywordLatency.get(), vectorLatency.get());
    }

    private int overlapCount(List<Document> a, List<Document> b) {
        Set<String> ids = new HashSet<>();
        for (Document d : a) ids.add(d.getId());
        int overlap = 0;
        for (Document d : b) if (ids.contains(d.getId())) overlap++;
        return overlap;
    }

    protected abstract List<Document> getDocumentsByKeywords(HybridSearchRequest request);

    protected abstract List<Document> getDocumentsBySemantics(HybridSearchRequest request);
}
