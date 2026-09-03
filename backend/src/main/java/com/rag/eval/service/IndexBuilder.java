package com.rag.eval.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class IndexBuilder {

    private static final Logger log = LoggerFactory.getLogger(IndexBuilder.class);

    private final ElasticsearchClient esClient;
    private final com.rag.eval.repository.VectorChunkRepo vectorChunkRepo;
    private final DashScopeService dashScope;
    private final String esIndexName;

    public IndexBuilder(ElasticsearchClient esClient,
                        com.rag.eval.repository.VectorChunkRepo vectorChunkRepo,
                        DashScopeService dashScope,
                        @Value("${elasticsearch.index-name}") String esIndexName) {
        this.esClient = esClient;
        this.vectorChunkRepo = vectorChunkRepo;
        this.dashScope = dashScope;
        this.esIndexName = esIndexName;
    }

    public void buildIndex(List<ChunkData> chunks) {
        List<List<Double>> embeddings = embed(chunks);
        write(chunks, embeddings);
    }

    /** Embed every chunk via DashScope, preserving order. Fails loudly so callers can
     *  abort BEFORE destroying existing data (see RebuildService). */
    public List<List<Double>> embed(List<ChunkData> chunks) {
        log.info("Embedding {} chunks...", chunks.size());
        List<List<Double>> all = new ArrayList<>();
        // DashScope text-embedding-v3 rejects batches larger than 10
        int batchSize = 10;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<ChunkData> batch = chunks.subList(i, end);
            List<String> texts = batch.stream().map(this::contextualText).toList();
            all.addAll(dashScope.embedBatch(texts));
            log.info("Embedded {}/{} chunks", end, chunks.size());
        }
        if (all.size() != chunks.size()) {
            throw new IllegalStateException(
                "embedding 返回条数 (" + all.size() + ") 与 chunk 数 (" + chunks.size() + ") 不一致，中止避免静默丢数据");
        }
        log.info("Embedding complete.");
        return all;
    }

    /** Contextual Retrieval：embedding 输入拼上「文件名 + 章节」前缀，
     *  使向量携带语境信息；存储的 content 字段保持不变。 */
    private String contextualText(ChunkData c) {
        StringBuilder s = new StringBuilder(c.getFileName());
        if (c.getChapter() != null && !c.getChapter().isBlank()) s.append(" · ").append(c.getChapter());
        if (c.getSection() != null && !c.getSection().isBlank()) s.append(" · ").append(c.getSection());
        return s.append("\n").append(c.getContent()).toString();
    }

    /** Dual-write embedded chunks to ES + pgvector. */
    public void write(List<ChunkData> chunks, List<List<Double>> embeddings) {
        log.info("Writing {} chunks...", chunks.size());
        int batchSize = 10;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<ChunkData> batch = chunks.subList(i, end);
            List<List<Double>> batchEmbs = embeddings.subList(i, end);

            indexToES(batch, batchEmbs);

            for (int j = 0; j < batch.size() && j < batchEmbs.size(); j++) {
                ChunkData chunk = batch.get(j);
                String embStr = DashScopeService.embeddingToString(batchEmbs.get(j));
                vectorChunkRepo.insert(
                    chunk.getChunkId(), chunk.getFileName(), chunk.getSourceType(),
                    chunk.getLanguage(), chunk.getChapter(), chunk.getSection(),
                    chunk.getChunkIndex(), chunk.getContent(), embStr
                );
            }

            log.info("Wrote {}/{} chunks", end, chunks.size());
        }
        log.info("Write complete.");
    }

    private void indexToES(List<ChunkData> batch, List<List<Double>> embeddings) {
        try {
            var bulkBuilder = new BulkRequest.Builder();
            // Refresh so freshly indexed chunks are immediately searchable — a
            // delete-by-query that runs right after would otherwise miss chunks
            // that are still sitting in an unrefreshed segment.
            bulkBuilder.refresh(Refresh.True);
            for (int i = 0; i < batch.size(); i++) {
                ChunkData chunk = batch.get(i);
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("chunk_id", chunk.getChunkId());
                doc.put("file_name", chunk.getFileName());
                doc.put("source_type", chunk.getSourceType());
                doc.put("language", chunk.getLanguage());
                doc.put("chapter", chunk.getChapter() != null ? chunk.getChapter() : "");
                doc.put("section", chunk.getSection() != null ? chunk.getSection() : "");
                doc.put("content", chunk.getContent());
                if (i < embeddings.size() && embeddings.get(i) != null) {
                    doc.put("embedding", embeddings.get(i));
                }
                final int chunkIdx = i;
                bulkBuilder.operations(op -> op
                    .index(idx -> idx
                        .index(esIndexName)
                        .id(chunk.getChunkId())
                        .document(doc)));
            }
            esClient.bulk(bulkBuilder.build());
        } catch (Exception e) {
            // 双写语义下，ES 写入失败不能静默吞掉，否则切到 ES 后端才发现缺数据。
            log.error("ES indexing failed — ES 与 pgvector 将不同步: {}", e.getMessage(), e);
            throw new RuntimeException("ES indexing failed: " + e.getMessage(), e);
        }
    }
}
