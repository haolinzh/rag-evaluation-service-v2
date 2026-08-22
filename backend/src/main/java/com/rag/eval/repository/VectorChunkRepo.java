package com.rag.eval.repository;

import com.rag.eval.model.ChunkPreview;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;

@Repository
public class VectorChunkRepo {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate txTemplate;

    public VectorChunkRepo(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.txTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
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

    public List<ChunkPreview> findPreviewsByFileName(String fileName) {
        String sql = """
            SELECT chunk_index, chapter, section, content
            FROM vector_chunks
            WHERE file_name = ?
            ORDER BY chunk_index
            """;
        return jdbc.query(sql, (rs, rowNum) -> new ChunkPreview(
            rs.getInt("chunk_index"),
            rs.getString("chapter"),
            rs.getString("section"),
            rs.getString("content")
        ), fileName);
    }

    public List<VectorSearchRow> similaritySearch(String queryEmbedding, double threshold, int topK,
                                                  String indexType, int probes, int efSearch) {
        // SET LOCAL 只在当前事务内生效，须与查询跑在同一连接、同一事务里；用
        // TransactionTemplate + DataSourceTransactionManager 显式包一层，避免 JPA
        // 事务管理器与 JdbcTemplate 连接脱节导致 probes/ef_search 静默不生效。
        return txTemplate.execute(status -> {
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
        });
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
