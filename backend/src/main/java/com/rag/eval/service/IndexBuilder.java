package com.rag.eval.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class IndexBuilder {

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
        System.out.println("Indexing " + chunks.size() + " chunks...");

        // DashScope text-embedding-v3 rejects batches larger than 10
        int batchSize = 10;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<ChunkData> batch = chunks.subList(i, end);

            List<String> texts = batch.stream().map(ChunkData::getContent).toList();

            // Get embeddings via DashScope
            List<List<Double>> embeddings = dashScope.embedBatch(texts);

            // Index to ES
            indexToES(batch, embeddings);

            // Index to pgvector
            for (int j = 0; j < batch.size() && j < embeddings.size(); j++) {
                ChunkData chunk = batch.get(j);
                String embStr = DashScopeService.embeddingToString(embeddings.get(j));
                vectorChunkRepo.insert(
                    chunk.getChunkId(), chunk.getFileName(), chunk.getSourceType(),
                    chunk.getLanguage(), chunk.getChapter(), chunk.getSection(),
                    chunk.getChunkIndex(), chunk.getContent(), embStr
                );
            }

            System.out.printf("Indexed %d/%d chunks%n", end, chunks.size());
        }

        System.out.println("Indexing complete.");
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
            System.err.println("ES indexing failed: " + e.getMessage());
        }
    }
}
