package com.rag.eval.service;

import com.rag.eval.model.ChunkConfig;
import com.rag.eval.model.DocumentIngestPermanentException;
import com.rag.eval.model.DocumentMeta;
import com.rag.eval.repository.DocumentMetaRepo;
import com.rag.eval.repository.VectorChunkRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class DocumentIngestWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestWorker.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BASE_SECONDS = 15;
    private static final long RETRY_CAP_SECONDS = 300;

    private final DocumentMetaRepo docRepo;
    private final DocumentParserService parser;
    private final IndexBuilder indexBuilder;
    private final VectorChunkRepo vectorChunkRepo;
    private final ElasticsearchService esService;
    private final SemanticCacheService cacheService;
    private final FileStorageService fileStorage;
    private final NotificationService notificationService;

    public DocumentIngestWorker(DocumentMetaRepo docRepo, DocumentParserService parser,
                                IndexBuilder indexBuilder, VectorChunkRepo vectorChunkRepo,
                                ElasticsearchService esService, SemanticCacheService cacheService,
                                FileStorageService fileStorage, NotificationService notificationService) {
        this.docRepo = docRepo;
        this.parser = parser;
        this.indexBuilder = indexBuilder;
        this.vectorChunkRepo = vectorChunkRepo;
        this.esService = esService;
        this.cacheService = cacheService;
        this.fileStorage = fileStorage;
        this.notificationService = notificationService;
    }

    public void process(Long id) {
        DocumentMeta meta = docRepo.findById(id).orElse(null);
        if (meta == null || !DocumentService.STATUS_PROCESSING.equals(meta.getStatus())) {
            return;
        }

        try {
            byte[] bytes = fileStorage.load(meta.getStoredFileName());
            if (bytes == null) {
                throw new DocumentIngestPermanentException("原始文件缺失: " + meta.getFileName());
            }
            DocumentParserService.ParsedDocument parsed;
            try {
                parsed = parser.parse(new ByteArrayInputStream(bytes), meta.getFileName());
            } catch (Exception e) {
                throw new DocumentIngestPermanentException("解析失败: " + e.getMessage());
            }
            ChunkConfig chunkConfig = chunkConfigOf(meta);
            List<ChunkData> chunks = parser.splitAndEnrich(parsed.text(), meta.getFileName(), parsed.sourceType(), chunkConfig);
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).setChunkIndex(i);
            }

            esService.ensureVectorIndex();
            clearIndexedData(meta);
            indexBuilder.buildIndex(chunks);

            int updated = docRepo.markReady(id, chunks.size());
            if (updated == 1) {
                notificationService.notify("document", "文档处理完成",
                    "「" + meta.getFileName() + "」分块完成（" + chunks.size() + " chunk）",
                    meta.getOwnerId(), meta.getOwnerName(), null);
            } else {
                // 处理期间文档被删除/替换：清掉本 worker 刚写入的孤儿 chunk。
                clearIndexedData(meta);
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "处理失败" : e.getMessage();
            if (isRetriable(e) && meta.getAttemptCount() < MAX_ATTEMPTS) {
                LocalDateTime nextRetry = computeNextRetry(meta.getAttemptCount());
                docRepo.markRetry(id, nextRetry, msg);
                log.warn("Document ingest retry scheduled: id={}, file={}, attempt={}, next={}",
                    id, meta.getFileName(), meta.getAttemptCount(), nextRetry);
            } else {
                clearIndexedData(meta);
                int updated = docRepo.markFailed(id, msg);
                if (updated == 1) {
                    notificationService.notify("document", "文档处理失败",
                        "「" + meta.getFileName() + "」" + msg,
                        meta.getOwnerId(), meta.getOwnerName(), null);
                }
                log.warn("Document ingest failed permanently: id={}, file={}", id, meta.getFileName(), e);
            }
        }
    }

    private boolean isRetriable(Exception e) {
        return !(e instanceof DocumentIngestPermanentException);
    }

    private LocalDateTime computeNextRetry(int attempt) {
        long delay = Math.min(RETRY_BASE_SECONDS << (attempt - 1), RETRY_CAP_SECONDS);
        long jitter = (long) (delay * 0.2 * ThreadLocalRandom.current().nextDouble());
        return LocalDateTime.now().plusSeconds(delay + jitter);
    }

    private ChunkConfig chunkConfigOf(DocumentMeta meta) {
        String splitMode = meta.getSplitMode() != null ? meta.getSplitMode() : ChunkConfig.MODE_SIZE;
        int chunkSize = meta.getChunkSize() != null ? meta.getChunkSize() : ChunkConfig.DEFAULT_CHUNK_SIZE;
        int overlap = meta.getOverlap() != null ? meta.getOverlap() : ChunkConfig.DEFAULT_OVERLAP;
        String delimiter = meta.getDelimiter() != null ? meta.getDelimiter() : "";
        return new ChunkConfig(splitMode, chunkSize, delimiter, overlap);
    }

    private void clearIndexedData(DocumentMeta meta) {
        vectorChunkRepo.deleteByFileName(meta.getFileName());
        esService.deleteByFileName(meta.getFileName());
        cacheService.clear();
    }
}
