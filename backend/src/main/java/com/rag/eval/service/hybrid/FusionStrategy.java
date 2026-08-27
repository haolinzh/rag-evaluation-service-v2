package com.rag.eval.service.hybrid;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Fuses multiple ranked result lists (keyword, semantic, ...) into a single top-K list.
 * Ported from the Spring AI Alibaba DataAgent reference implementation.
 */
public interface FusionStrategy {

    List<Document> fuseResults(int topK, List<Document>... resultLists);
}
