package com.rag.eval.service;

import com.rag.eval.model.ChunkConfig;
import com.rag.eval.model.ChunkPreview;
import com.rag.eval.model.DocumentMeta;
import com.rag.eval.repository.DocumentMetaRepo;
import com.rag.eval.repository.VectorChunkRepo;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DocumentService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    private static final String EMBEDDING_MODEL_KEY = "dashscope.embedding-model";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-v3";
    // 维度已锁定 1024（不支持多维度 embedding 模型），与 ConfigController.EMBEDDING_DIMENSION 对齐。
    private static final int EMBEDDING_DIMENSION = 1024;

    private final DocumentParserService parser;
    private final IndexBuilder indexBuilder;
    private final DocumentMetaRepo docRepo;
    private final VectorChunkRepo vectorChunkRepo;
    private final ElasticsearchService esService;
    private final SemanticCacheService cacheService;
    private final FileStorageService fileStorage;
    private final ConfigService configService;
    // 串行处理入库任务：避免并发 embedding 限流与双写冲突，大文件按序排队。
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DocumentService(DocumentParserService parser, IndexBuilder indexBuilder,
                           DocumentMetaRepo docRepo, VectorChunkRepo vectorChunkRepo,
                           ElasticsearchService esService, SemanticCacheService cacheService,
                           FileStorageService fileStorage, ConfigService configService) {
        this.parser = parser;
        this.indexBuilder = indexBuilder;
        this.docRepo = docRepo;
        this.vectorChunkRepo = vectorChunkRepo;
        this.esService = esService;
        this.cacheService = cacheService;
        this.fileStorage = fileStorage;
        this.configService = configService;
    }

    public record OriginalFile(String fileName, byte[] bytes) {}

    public DocumentMeta ingest(MultipartFile file) throws Exception {
        return ingest(file, ChunkConfig.defaults());
    }

    public DocumentMeta ingest(MultipartFile file, ChunkConfig config) throws Exception {
        return ingestBytes(file.getOriginalFilename(), file.getBytes(), file.getSize(), config);
    }

    public DocumentMeta ingestBytes(String fileName, byte[] bytes, long fileSize, ChunkConfig config) throws Exception {
        // Same-name re-upload replaces the previous version.
        DocumentMeta meta = docRepo.findByFileName(fileName).orElse(null);
        if (meta != null) {
            deleteIndexedData(meta);
        } else {
            meta = new DocumentMeta();
        }

        // Persist the original file now, then hand the heavy parse+embed work to a
        // background thread so the upload returns immediately with a PENDING status.
        String storedFileName = fileStorage.store(fileName, bytes);

        meta.setFileName(fileName);
        meta.setFileSize(fileSize);
        meta.setChunkCount(null);
        meta.setSplitMode(config.splitMode());
        meta.setChunkSize(config.chunkSize());
        meta.setOverlap(config.overlap());
        meta.setDelimiter(config.isDelimiterMode() ? config.delimiter() : null);
        meta.setStoredFileName(storedFileName);
        meta.setEmbeddingModel(configService.get(EMBEDDING_MODEL_KEY, DEFAULT_EMBEDDING_MODEL));
        meta.setEmbeddingDimension(EMBEDDING_DIMENSION);
        meta.setStatus(STATUS_PENDING);
        meta.setErrorMessage(null);
        meta = docRepo.save(meta);

        final DocumentMeta saved = meta;
        executor.execute(() -> processAsync(saved));
        return saved;
    }

    private void processAsync(DocumentMeta meta) {
        try {
            byte[] bytes = fileStorage.load(meta.getStoredFileName());
            if (bytes == null) {
                throw new IllegalStateException("原始文件缺失: " + meta.getFileName());
            }
            DocumentParserService.ParsedDocument parsed =
                parser.parse(new ByteArrayInputStream(bytes), meta.getFileName());
            ChunkConfig chunkConfig = chunkConfigOf(meta);
            List<ChunkData> chunks = parser.splitAndEnrich(parsed.text(), meta.getFileName(), parsed.sourceType(), chunkConfig);
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).setChunkIndex(i);
            }

            // Ensure ES index has a dense_vector mapping before dual-writing embeddings.
            esService.ensureVectorIndex();
            // 重处理前清掉旧 chunk/向量，避免反复重切时残留脏数据。
            clearIndexedData(meta);
            indexBuilder.buildIndex(chunks);

            meta.setStatus(STATUS_READY);
            meta.setChunkCount(chunks.size());
            meta.setErrorMessage(null);
            docRepo.save(meta);
        } catch (Exception e) {
            // 清理可能的部分写入，避免脏数据进入检索；文件保留，删除文档时统一清。
            clearIndexedData(meta);
            meta.setStatus(STATUS_FAILED);
            meta.setErrorMessage(e.getMessage());
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

    public List<DocumentMeta> listAll() {
        // 最新的排前面，避免新增文档被挤到分页第二页而“看不到”。
        return docRepo.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public List<ChunkPreview> getChunkPreviews(Long id) {
        return docRepo.findById(id)
            .map(m -> vectorChunkRepo.findPreviewsByFileName(m.getFileName()))
            .orElse(List.of());
    }

    public void deleteById(Long id) {
        docRepo.findById(id).ifPresent(meta -> {
            deleteIndexedData(meta);
            docRepo.delete(meta);
        });
    }

    public Optional<OriginalFile> getOriginal(Long id) {
        return docRepo.findById(id).flatMap(m -> {
            try {
                byte[] bytes = fileStorage.load(m.getStoredFileName());
                return bytes == null ? Optional.empty() : Optional.of(new OriginalFile(m.getFileName(), bytes));
            } catch (Exception e) {
                System.err.println("Failed to load original file: " + e.getMessage());
                return Optional.empty();
            }
        });
    }

    public DocumentMeta reprocess(Long id, ChunkConfig config) {
        DocumentMeta meta = docRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + id));
        // 清掉旧 chunk 与向量（原文件保留，直接复用重切），改配置后重新入列处理。
        clearIndexedData(meta);
        meta.setSplitMode(config.splitMode());
        meta.setChunkSize(config.chunkSize());
        meta.setOverlap(config.overlap());
        meta.setDelimiter(config.isDelimiterMode() ? config.delimiter() : null);
        meta.setEmbeddingModel(configService.get(EMBEDDING_MODEL_KEY, DEFAULT_EMBEDDING_MODEL));
        meta.setEmbeddingDimension(EMBEDDING_DIMENSION);
        meta.setChunkCount(null);
        meta.setStatus(STATUS_PENDING);
        meta.setErrorMessage(null);
        meta = docRepo.save(meta);
        final DocumentMeta saved = meta;
        executor.execute(() -> processAsync(saved));
        return saved;
    }

    private void clearIndexedData(DocumentMeta meta) {
        vectorChunkRepo.deleteByFileName(meta.getFileName());
        esService.deleteByFileName(meta.getFileName());
        cacheService.clear();
    }

    private void deleteIndexedData(DocumentMeta meta) {
        clearIndexedData(meta);
        fileStorage.delete(meta.getStoredFileName());
    }
}
