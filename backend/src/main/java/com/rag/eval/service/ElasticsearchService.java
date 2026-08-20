package com.rag.eval.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.rag.eval.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ElasticsearchService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchService.class);

    private final ElasticsearchClient esClient;
    private final ConfigService config;
    private final String esIndexName;

    public ElasticsearchService(ElasticsearchClient esClient,
                                ConfigService config,
                                @Value("${elasticsearch.index-name}") String esIndexName) {
        this.esClient = esClient;
        this.config = config;
        this.esIndexName = esIndexName;
    }

    public void deleteByFileName(String fileName) {
        try {
            esClient.deleteByQuery(d -> d
                .index(esIndexName)
                .refresh(true)
                .query(q -> q.term(t -> t.field("file_name.keyword").value(fileName))));
        } catch (Exception e) {
            log.warn("ES delete failed: {}", e.getMessage());
        }
    }

    public List<SearchResult> keywordSearch(String query, int topK) {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                .index(esIndexName)
                .query(q -> q
                    .match(m -> m.field("content").query(query)))
                .size(topK));

            SearchResponse<Map> response = esClient.search(request, Map.class);
            List<SearchResult> results = new ArrayList<>();

            for (var hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;

                double score = hit.score() != null ? hit.score() : 0.0;
                results.add(SearchResult.builder()
                    .chunkId((String) source.get("chunk_id"))
                    .fileName((String) source.get("file_name"))
                    .chapter((String) source.get("chapter"))
                    .section((String) source.get("section"))
                    .content((String) source.get("content"))
                    .score(score)
                    .source("keyword")
                    .sourceDetails(new SearchResult.SourceDetail(score, null, null))
                    .build());
            }
            return results;
        } catch (Exception e) {
            log.error("ES keyword search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public void recreateIndex() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(esIndexName)).value();
            if (exists) {
                esClient.indices().delete(d -> d.index(esIndexName));
            }
            createIndex(embeddingDimension());
        } catch (Exception e) {
            throw new RuntimeException("ES index recreate failed: " + e.getMessage(), e);
        }
    }

    public void ensureVectorIndex() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(esIndexName)).value();
            if (!exists) {
                createIndex(embeddingDimension());
            }
            // 索引已存在时不动：存量旧索引缺少 dense_vector 映射的迁移只能显式重建，
            // 因为 ES 不允许对已有字段改类型，且删索引重建需全量重入库。
        } catch (Exception e) {
            throw new RuntimeException("ES index ensure failed: " + e.getMessage(), e);
        }
    }

    private void createIndex(int dims) {
        try {
            esClient.indices().create(c -> c
                .index(esIndexName)
                .mappings(m -> m
                    .properties("embedding", p -> p.denseVector(dv -> dv
                        .dims(dims)
                        .index(true)
                        .similarity("cosine")))));
        } catch (Exception e) {
            throw new RuntimeException("ES index create failed: " + e.getMessage(), e);
        }
    }

    private int embeddingDimension() {
        String model = config.get("dashscope.embedding-model", "text-embedding-v3");
        return switch (model) {
            case "text-embedding-v2" -> 1536;
            case "text-embedding-v4" -> 2048;
            default -> 1024;
        };
    }

    public List<SearchResult> knnSearch(List<Double> queryVector, int topK, int numCandidates, double threshold) {
        // ES kNN requires num_candidates >= k; the hybrid recall size can exceed the
        // configured default, so clamp up to avoid a silent empty result.
        int effectiveNumCandidates = Math.max(numCandidates, topK);
        try {
            List<Float> queryFloats = queryVector.stream()
                .map(Double::floatValue)
                .toList();
            SearchRequest request = SearchRequest.of(s -> s
                .index(esIndexName)
                .knn(k -> k
                    .field("embedding")
                    .queryVector(queryFloats)
                    .k((long) topK)
                    .numCandidates((long) effectiveNumCandidates))
                .minScore(threshold)
                .size(topK));

            SearchResponse<Map> response = esClient.search(request, Map.class);
            List<SearchResult> results = new ArrayList<>();

            for (var hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;

                double score = hit.score() != null ? hit.score() : 0.0;
                results.add(SearchResult.builder()
                    .chunkId((String) source.get("chunk_id"))
                    .fileName((String) source.get("file_name"))
                    .chapter((String) source.get("chapter"))
                    .section((String) source.get("section"))
                    .content((String) source.get("content"))
                    .score(score)
                    .source("semantic")
                    .sourceDetails(new SearchResult.SourceDetail(null, score, null))
                    .build());
            }
            return results;
        } catch (Exception e) {
            log.error("ES kNN search failed (索引可能缺少 dense_vector 映射或维度不匹配，需执行「重建向量索引」): {}",
                e.getMessage(), e);
            return List.of();
        }
    }
}
