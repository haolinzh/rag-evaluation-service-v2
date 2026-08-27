package com.rag.eval.integration;

import com.rag.eval.model.SearchResult;
import com.rag.eval.service.ConfigService;
import com.rag.eval.service.hybrid.DocumentSupport;
import com.rag.eval.service.hybrid.RrfFusionStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalIntegrationTest {

    private static RrfFusionStrategy rrf() {
        ConfigService config = mock(ConfigService.class);
        when(config.getInt("retrieval.rrf-k", 60)).thenReturn(60);
        return new RrfFusionStrategy(config);
    }

    @Test
    void fullRRFPipeline_mergeAndRank() {
        var rrf = rrf();

        var kwResults = List.of(
            result("chunk-1", "intro.pdf", "Spring AI is a framework...", 0.95, "keyword"),
            result("chunk-2", "setup.pdf", "To install Spring AI...", 0.88, "keyword"),
            result("chunk-3", "rag.pdf", "RAG combines retrieval...", 0.75, "keyword")
        );
        var vecResults = List.of(
            result("chunk-3", "rag.pdf", "RAG combines retrieval...", 0.92, "semantic"),
            result("chunk-1", "intro.pdf", "Spring AI is a framework...", 0.89, "semantic"),
            result("chunk-4", "advanced.pdf", "Advanced RAG techniques...", 0.82, "semantic")
        );

        List<Document> keywordDocs = kwResults.stream().map(DocumentSupport::toDocument).toList();
        List<Document> vectorDocs = vecResults.stream().map(DocumentSupport::toDocument).toList();

        List<Document> fused = rrf.fuseResults(3, keywordDocs, vectorDocs);
        List<SearchResult> results = fused.stream().map(d -> DocumentSupport.toSearchResult(d, "rrf")).toList();

        assertEquals(3, results.size());
        // chunk-1: keyword rank=1, vector rank=2 → highest RRF
        assertEquals("chunk-1", results.get(0).getChunkId());

        for (SearchResult r : results) {
            assertEquals("rrf", r.getSource());
            assertNotNull(r.getSourceDetails());
            assertNotNull(r.getSourceDetails().getRrfScore());
        }
    }

    @Test
    void partialOverlap_resultsStillRanked() {
        var rrf = rrf();

        var kwResults = List.of(result("A", "a.pdf", "content A", 0.9, "keyword"));
        var vecResults = List.of(result("B", "b.pdf", "content B", 0.9, "semantic"));

        List<Document> keywordDocs = kwResults.stream().map(DocumentSupport::toDocument).toList();
        List<Document> vectorDocs = vecResults.stream().map(DocumentSupport::toDocument).toList();

        List<Document> fused = rrf.fuseResults(2, keywordDocs, vectorDocs);
        assertEquals(2, fused.size());
        for (Document d : fused) {
            assertTrue(d.getScore() > 0);
        }
    }

    private SearchResult result(String chunkId, String fileName, String content, double score, String source) {
        return SearchResult.builder()
            .chunkId(chunkId)
            .fileName(fileName)
            .content(content)
            .score(score)
            .source(source)
            .sourceDetails(new SearchResult.SourceDetail(
                "keyword".equals(source) ? score : null,
                "semantic".equals(source) ? score : null,
                null))
            .build();
    }
}
