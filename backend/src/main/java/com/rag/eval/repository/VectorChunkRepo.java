package com.rag.eval.repository;

import com.rag.eval.model.ChunkPreview;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class VectorChunkRepo {

    private final JdbcTemplate jdbc;

    public VectorChunkRepo(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String chunkId, String fileName, String sourceType, String language,
                       String chapter, String section, int chunkIndex, String content, String embeddingStr) {
        jdbc.update(
            "INSERT INTO vector_chunks (chunk_id, file_name, source_type, language, chapter, section, chunk_index, content, embedding) " +
            "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?::vector)",
            chunkId, fileName, sourceType, language, chapter, section, chunkIndex, content, embeddingStr
        );
    }

    public void deleteByFileName(String fileName) {
        jdbc.update("DELETE FROM vector_chunks WHERE file_name = ?", fileName);
    }

    public List<ChunkPreview> findPreviewsByFileName(String fileName, int limit) {
        String sql = """
            SELECT chunk_index, chapter, section, content
            FROM vector_chunks
            WHERE file_name = ?
            ORDER BY chunk_index
            LIMIT ?
            """;
        return jdbc.query(sql, (rs, rowNum) -> new ChunkPreview(
            rs.getInt("chunk_index"),
            rs.getString("chapter"),
            rs.getString("section"),
            snippet(rs.getString("content"))
        ), fileName, limit);
    }

    private String snippet(String content) {
        if (content == null) return "";
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() > 150 ? flat.substring(0, 150) + "…" : flat;
    }

    @Transactional
    public List<VectorSearchRow> similaritySearch(String queryEmbedding, double threshold, int topK,
                                                  String indexType, int probes, int efSearch) {
        if ("hnsw".equalsIgnoreCase(indexType)) {
            jdbc.execute("SET LOCAL hnsw.ef_search = " + efSearch);
        } else {
            jdbc.execute("SET LOCAL ivfflat.probes = " + probes);
        }

        String sql = """
            SELECT chunk_id, file_name, chapter, section, content, source_type,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM vector_chunks
            WHERE 1 - (embedding <=> ?::vector) >= ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;
        return jdbc.query(sql,
            (rs, rowNum) -> new VectorSearchRow(
                rs.getString("chunk_id"),
                rs.getString("file_name"),
                rs.getString("chapter"),
                rs.getString("section"),
                rs.getString("content"),
                rs.getString("source_type"),
                rs.getDouble("similarity")
            ),
            queryEmbedding, queryEmbedding, threshold, queryEmbedding, topK
        );
    }

    public void truncate() {
        jdbc.update("TRUNCATE vector_chunks");
    }

    public void rebuildIndex(String indexType, int lists) {
        jdbc.execute("DROP INDEX IF EXISTS idx_vector_chunks_embedding");
        if ("hnsw".equalsIgnoreCase(indexType)) {
            jdbc.execute("CREATE INDEX idx_vector_chunks_embedding ON vector_chunks " +
                "USING hnsw (embedding vector_cosine_ops)");
        } else {
            jdbc.execute("CREATE INDEX idx_vector_chunks_embedding ON vector_chunks " +
                "USING ivfflat (embedding vector_cosine_ops) WITH (lists = " + lists + ")");
        }
    }

    public record VectorSearchRow(String chunkId, String fileName, String chapter, String section,
                                   String content, String sourceType, double similarity) {}
}
