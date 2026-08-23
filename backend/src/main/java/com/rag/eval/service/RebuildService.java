package com.rag.eval.service;

import com.rag.eval.model.ChunkConfig;
import com.rag.eval.model.DocumentMeta;
import com.rag.eval.repository.DocumentMetaRepo;
import com.rag.eval.repository.VectorChunkRepo;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RebuildService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";

    private final DocumentMetaRepo docRepo;
    private final FileStorageService fileStorage;
    private final DocumentParserService parser;
    private final IndexBuilder indexBuilder;
    private final VectorChunkRepo vectorChunkRepo;
    private final ElasticsearchService esService;
    private final SemanticCacheService cacheService;
    private final ConfigService config;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<RebuildStatus> status = new AtomicReference<>(
        new RebuildStatus(false, 0, 0, 0, "IDLE", null));

    public RebuildService(DocumentMetaRepo docRepo, FileStorageService fileStorage,
                          DocumentParserService parser, IndexBuilder indexBuilder,
                          VectorChunkRepo vectorChunkRepo, ElasticsearchService esService,
                          SemanticCacheService cacheService, ConfigService config) {
        this.docRepo = docRepo;
        this.fileStorage = fileStorage;
        this.parser = parser;
        this.indexBuilder = indexBuilder;
        this.vectorChunkRepo = vectorChunkRepo;
        this.esService = esService;
        this.cacheService = cacheService;
        this.config = config;
    }

    public record RebuildResult(int documentCount, int chunkCount) {}

    public record PgIndexResult(String indexType, int lists) {}

    public record RebuildStatus(boolean running, int processedDocuments, int totalDocuments,
                                int chunkCount, String phase, String message) {}

    private record PreparedDoc(List<ChunkData> chunks, List<List<Double>> embeddings) {}

    public RebuildStatus startRebuild() {
        if (status.get().running()) {
            throw new IllegalStateException("重建已在进行中，请稍候");
        }
        List<DocumentMeta> docs = docRepo.findAll();
        for (DocumentMeta meta : docs) {
            meta.setStatus(STATUS_PENDING);
            meta.setErrorMessage(null);
            docRepo.save(meta);
        }
        status.set(new RebuildStatus(true, 0, docs.size(), 0, "PREPARING", null));
        executor.execute(this::runRebuild);
        return status.get();
    }

    public RebuildStatus getStatus() {
        return status.get();
    }

    public PgIndexResult rebuildPgIndex() {
        if (status.get().running()) {
            throw new IllegalStateException("向量索引重建进行中，请稍候再重建 PG 索引");
        }
        String indexType = config.get("vector.pgvector.index-type", "ivfflat");
        int lists = config.getInt("vector.pgvector.lists", 100);
        vectorChunkRepo.rebuildIndex(indexType, lists);
        cacheService.clear();
        return new PgIndexResult(indexType, lists);
    }

    private void runRebuild() {
        String indexType = config.get("vector.pgvector.index-type", "ivfflat");
        int lists = config.getInt("vector.pgvector.lists", 100);

        List<DocumentMeta> docs = docRepo.findAll();
        int total = docs.size();
        boolean dataCleared = false;

        try {
            // Phase 1: load + parse + split + EMBED every document BEFORE destroying anything.
            // Any missing original, parse error, or embedding failure aborts here,
            // leaving current data intact.
            List<PreparedDoc> prepared = new ArrayList<>();
            int processed = 0;
            for (DocumentMeta meta : docs) {
                status.set(new RebuildStatus(true, processed, total, 0, "PREPARING", meta.getFileName()));
                byte[] bytes = fileStorage.load(meta.getStoredFileName());
                if (bytes == null) {
                    throw new IllegalStateException("原始文件缺失，无法重建: " + meta.getFileName());
                }

                DocumentParserService.ParsedDocument parsed =
                    parser.parse(new ByteArrayInputStream(bytes), meta.getFileName());
                ChunkConfig chunkConfig = chunkConfigOf(meta);
                List<ChunkData> chunks = parser.splitAndEnrich(parsed.text(), meta.getFileName(), parsed.sourceType(), chunkConfig);
                for (int i = 0; i < chunks.size(); i++) {
                    chunks.get(i).setChunkIndex(i);
                }
                List<List<Double>> embeddings = indexBuilder.embed(chunks);
                prepared.add(new PreparedDoc(chunks, embeddings));
                processed++;
                status.set(new RebuildStatus(true, processed, total, 0, "PREPARING", meta.getFileName()));
            }

            // Phase 2: clear both stores, then re-ingest from the prepared chunks (dual write).
            vectorChunkRepo.truncate();
            esService.recreateIndex();
            dataCleared = true;

            int chunkCount = 0;
            int written = 0;
            for (PreparedDoc doc : prepared) {
                status.set(new RebuildStatus(true, written, total, chunkCount, "WRITING", null));
                indexBuilder.write(doc.chunks(), doc.embeddings());
                chunkCount += doc.embeddings().size();
                written++;
            }

            // Phase 3: rebuild the pgvector index AFTER data is loaded so IVFFlat can cluster
            // against populated data (better recall than building on an empty table).
            status.set(new RebuildStatus(true, total, total, chunkCount, "INDEXING", null));
            vectorChunkRepo.rebuildIndex(indexType, lists);

            cacheService.clear();

            markAll(STATUS_READY, null);
            status.set(new RebuildStatus(false, total, total, chunkCount, "DONE", null));
        } catch (Exception e) {
            // 清库前失败：数据仍完整，恢复为就绪；清库后失败：数据可能不完整，标记失败。
            if (dataCleared) {
                markAll(STATUS_FAILED, "重建失败: " + e.getMessage());
            } else {
                markAll(STATUS_READY, null);
            }
            status.set(new RebuildStatus(false, 0, 0, 0, "FAILED", e.getMessage()));
        }
    }

    private void markAll(String status, String errorMessage) {
        for (DocumentMeta meta : docRepo.findAll()) {
            meta.setStatus(status);
            meta.setErrorMessage(errorMessage);
            docRepo.save(meta);
        }
    }

    private ChunkConfig chunkConfigOf(DocumentMeta meta) {
        String splitMode = meta.getSplitMode() != null ? meta.getSplitMode() : ChunkConfig.MODE_SIZE;
        int chunkSize = meta.getChunkSize() != null ? meta.getChunkSize() : ChunkConfig.DEFAULT_CHUNK_SIZE;
        int overlap = meta.getOverlap() != null ? meta.getOverlap() : ChunkConfig.DEFAULT_OVERLAP;
        String delimiter = meta.getDelimiter() != null ? meta.getDelimiter() : "";
        return new ChunkConfig(splitMode, chunkSize, delimiter, overlap);
    }
}
