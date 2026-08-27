package com.rag.eval.service.hybrid;

import com.rag.eval.model.SearchResult;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared metadata keys and {@code Document <-> SearchResult} mapping for the hybrid
 * retrieval pipeline. Keyword / semantic / rrf scores are carried through
 * {@link Document} metadata so the confidence gate can reconstruct the original
 * 0..1 similarity signal after fusion.
 */
public final class DocumentSupport {

    public static final String META_FILE_NAME = "file_name";
    public static final String META_CHAPTER = "chapter";
    public static final String META_SECTION = "section";
    public static final String META_KEYWORD_SCORE = "keyword_score";
    public static final String META_SEMANTIC_SCORE = "semantic_score";
    public static final String META_RRF_SCORE = "rrf_score";

    private DocumentSupport() {}

    public static Document document(String id, String text, String fileName, String chapter,
                                    String section, Map<String, Object> scores, double score) {
        Document.Builder b = Document.builder().id(id).text(text);
        put(b, META_FILE_NAME, fileName);
        put(b, META_CHAPTER, chapter);
        put(b, META_SECTION, section);
        if (scores != null) {
            scores.forEach((k, v) -> put(b, k, v));
        }
        return b.score(score).build();
    }

    public static Document toDocument(SearchResult sr) {
        Map<String, Object> scores = new HashMap<>();
        SearchResult.SourceDetail d = sr.getSourceDetails();
        if (d != null) {
            if (d.getKeywordScore() != null) scores.put(META_KEYWORD_SCORE, d.getKeywordScore());
            if (d.getSemanticScore() != null) scores.put(META_SEMANTIC_SCORE, d.getSemanticScore());
            if (d.getRrfScore() != null) scores.put(META_RRF_SCORE, d.getRrfScore());
        }
        return document(sr.getChunkId(), sr.getContent(), sr.getFileName(), sr.getChapter(),
            sr.getSection(), scores, sr.getScore());
    }

    public static SearchResult toSearchResult(Document doc, String source) {
        Map<String, Object> m = doc.getMetadata();
        return SearchResult.builder()
            .chunkId(doc.getId())
            .fileName(str(m.get(META_FILE_NAME)))
            .chapter(str(m.get(META_CHAPTER)))
            .section(str(m.get(META_SECTION)))
            .content(doc.getText())
            .score(doc.getScore() != null ? doc.getScore() : 0.0)
            .source(source)
            .sourceDetails(new SearchResult.SourceDetail(
                doubleVal(m.get(META_KEYWORD_SCORE)),
                doubleVal(m.get(META_SEMANTIC_SCORE)),
                doubleVal(m.get(META_RRF_SCORE))))
            .build();
    }

    static Double doubleVal(Document doc, String key) {
        return doubleVal(doc.getMetadata().get(key));
    }

    private static void put(Document.Builder b, String key, Object value) {
        if (value != null) {
            b.metadata(key, value);
        }
    }

    private static String str(Object v) {
        return v != null ? v.toString() : null;
    }

    private static Double doubleVal(Object v) {
        return v instanceof Number n ? n.doubleValue() : null;
    }
}
