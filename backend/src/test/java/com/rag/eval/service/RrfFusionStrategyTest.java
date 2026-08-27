package com.rag.eval.service;

import com.rag.eval.model.SearchResult;
import com.rag.eval.service.hybrid.DocumentSupport;
import com.rag.eval.service.hybrid.RrfFusionStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RrfFusionStrategyTest {

    private static RrfFusionStrategy rrfWithK(int k) {
        ConfigService config = mock(ConfigService.class);
        when(config.getInt("retrieval.rrf-k", 60)).thenReturn(k);
        return new RrfFusionStrategy(config);
    }

    private final RrfFusionStrategy rrfStrategy = rrfWithK(60);

    private static Document keyword(String chunkId, String fileName, String content, double score) {
        return DocumentSupport.toDocument(SearchResult.builder()
            .chunkId(chunkId).fileName(fileName).content(content).score(score)
            .source("keyword")
            .sourceDetails(new SearchResult.SourceDetail(score, null, null))
            .build());
    }

    private static Document semantic(String chunkId, String fileName, String content, double score) {
        return DocumentSupport.toDocument(SearchResult.builder()
            .chunkId(chunkId).fileName(fileName).content(content).score(score)
            .source("semantic")
            .sourceDetails(new SearchResult.SourceDetail(null, score, null))
            .build());
    }

    @Test
    void fuse_twoLists_returnsTopKResults() {
        var kw = List.of(
            keyword("A", "doc1.pdf", "content A", 0.9),
            keyword("B", "doc2.pdf", "content B", 0.8),
            keyword("C", "doc3.pdf", "content C", 0.7)
        );
        var vec = List.of(
            semantic("B", "doc2.pdf", "content B", 0.95),
            semantic("A", "doc1.pdf", "content A", 0.85),
            semantic("D", "doc4.pdf", "content D", 0.80)
        );

        List<Document> fused = rrfStrategy.fuseResults(3, kw, vec);

        assertEquals(3, fused.size());
        // "A": keyword rank=1, vector rank=2 → RRF = 1/61 + 1/62 ≈ 0.0325
        // "B": keyword rank=2, vector rank=1 → RRF = 1/62 + 1/61 ≈ 0.0325 (tied with A)
        // Ties are broken by insertion order (keyword processed first, A at rank 1)
        assertEquals("A", fused.get(0).getId());
        assertEquals("B", fused.get(1).getId());
    }

    @Test
    void fuse_emptyInput_returnsEmpty() {
        assertTrue(rrfStrategy.fuseResults(5, List.of(), List.of()).isEmpty());
    }

    @Test
    void fuse_oneEmptyList_returnsFromOther() {
        var kw = List.of(keyword("A", "f.pdf", "c", 0.9));
        List<Document> fused = rrfStrategy.fuseResults(5, kw, List.of());
        assertEquals(1, fused.size());
        assertEquals("A", fused.get(0).getId());
    }

    @Test
    void fuse_topKExceedsAvailable_returnsAll() {
        var kw = List.of(keyword("X", "f.pdf", "c", 0.5));
        assertEquals(1, rrfStrategy.fuseResults(10, kw, List.of()).size());
    }

    @Test
    void rrfK_affectsScore() {
        RrfFusionStrategy largeK = rrfWithK(100);
        var kw = List.of(keyword("A", "f.pdf", "c", 0.9));
        var fused60 = rrfStrategy.fuseResults(1, kw, List.of());
        var fused100 = largeK.fuseResults(1, kw, List.of());

        // Larger k makes scores smaller
        assertTrue(fused100.get(0).getScore() < fused60.get(0).getScore());
    }

    @Test
    void fusedDocument_carriesAllSourceScores() {
        var kw = List.of(keyword("A", "a.pdf", "c", 0.9));
        var vec = List.of(semantic("A", "a.pdf", "c", 0.8));
        List<Document> fused = rrfStrategy.fuseResults(5, kw, vec);

        SearchResult r = DocumentSupport.toSearchResult(fused.get(0), "rrf");
        assertEquals("rrf", r.getSource());
        assertNotNull(r.getSourceDetails());
        assertNotNull(r.getSourceDetails().getRrfScore());
        assertNotNull(r.getSourceDetails().getKeywordScore());
        assertNotNull(r.getSourceDetails().getSemanticScore());
    }
}
