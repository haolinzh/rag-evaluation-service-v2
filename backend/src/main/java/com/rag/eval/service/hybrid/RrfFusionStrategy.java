package com.rag.eval.service.hybrid;

import com.rag.eval.service.ConfigService;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (k=60) over Spring AI {@link Document} lists. Replaces the old
 * {@code RRFusionService}, preserving the semantic score from the vector leg onto chunks
 * also matched by keyword search so the confidence gate keeps a 0..1 signal.
 */
@Service
public class RrfFusionStrategy implements FusionStrategy {

    private final ConfigService config;

    public RrfFusionStrategy(ConfigService config) {
        this.config = config;
    }

    @Override
    public List<Document> fuseResults(int topK, List<Document>... resultLists) {
        int rrfK = config.getInt("retrieval.rrf-k", 60);
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, Document> docLookup = new LinkedHashMap<>();

        for (List<Document> list : resultLists) {
            for (int i = 0; i < list.size(); i++) {
                Document doc = list.get(i);
                double contribution = 1.0 / (rrfK + i + 1);
                rrfScores.merge(doc.getId(), contribution, Double::sum);

                Document existing = docLookup.get(doc.getId());
                if (existing == null) {
                    docLookup.put(doc.getId(), doc);
                } else {
                    mergeScore(existing, doc, DocumentSupport.META_KEYWORD_SCORE);
                    mergeScore(existing, doc, DocumentSupport.META_SEMANTIC_SCORE);
                }
            }
        }

        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> fusedDocument(docLookup.get(e.getKey()), e.getValue()))
            .toList();
    }

    private void mergeScore(Document target, Document source, String key) {
        Double value = DocumentSupport.doubleVal(source, key);
        if (value != null) {
            target.getMetadata().put(key, value);
        }
    }

    private Document fusedDocument(Document original, double rrfScore) {
        return Document.builder()
            .id(original.getId())
            .text(original.getText())
            .metadata(original.getMetadata())
            .metadata(DocumentSupport.META_RRF_SCORE, rrfScore)
            .score(rrfScore)
            .build();
    }
}
