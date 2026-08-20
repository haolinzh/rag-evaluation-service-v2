package com.rag.eval.service;

import com.rag.eval.model.SearchResult;
import com.rag.eval.repository.VectorChunkRepo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("pgvector")
public class PgVectorVectorStore implements VectorStore {

    private final VectorChunkRepo vectorChunkRepo;
    private final ConfigService config;

    public PgVectorVectorStore(VectorChunkRepo vectorChunkRepo, ConfigService config) {
        this.vectorChunkRepo = vectorChunkRepo;
        this.config = config;
    }

    @Override
    public List<SearchResult> search(String queryEmbedding, int topK, double threshold) {
        String indexType = config.get("vector.pgvector.index-type", "ivfflat");
        int probes = config.getInt("vector.pgvector.probes", 1);
        int efSearch = config.getInt("vector.pgvector.ef-search", 40);

        List<VectorChunkRepo.VectorSearchRow> rows =
            vectorChunkRepo.similaritySearch(queryEmbedding, threshold, topK, indexType, probes, efSearch);

        return rows.stream()
            .map(row -> SearchResult.builder()
                .chunkId(row.chunkId())
                .fileName(row.fileName())
                .chapter(row.chapter())
                .section(row.section())
                .content(row.content())
                .score(row.similarity())
                .source("semantic")
                .sourceDetails(new SearchResult.SourceDetail(null, row.similarity(), null))
                .build())
            .toList();
    }

    @Override
    public String backend() {
        return "pgvector";
    }
}
