package com.rag.eval.service;

import com.rag.eval.model.ChunkConfig;
import com.rag.eval.model.DocumentMeta;
import com.rag.eval.repository.DocumentMetaRepo;
import com.rag.eval.repository.VectorChunkRepo;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class RebuildService {

    private final DocumentMetaRepo docRepo;
    private final FileStorageService fileStorage;
    private final DocumentParserService parser;
    private final IndexBuilder indexBuilder;
    private final VectorChunkRepo vectorChunkRepo;
    private final ElasticsearchService esService;
    private final SemanticCacheService cacheService;
    private final ConfigService config;

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

    private record PreparedDoc(List<ChunkData> chunks, List<List<Double>> embeddings) {}

    public RebuildResult rebuildVectorIndex() throws Exception {
        String indexType = config.get("vector.pgvector.index-type", "ivfflat");
        int lists = config.getInt("vector.pgvector.lists", 100);

        // Phase 1: load + parse + split + EMBED every document BEFORE destroying anything.
        // Any missing original, parse error, or embedding failure aborts here,
        // leaving current data intact.
        List<PreparedDoc> prepared = new ArrayList<>();
        for (DocumentMeta meta : docRepo.findAll()) {
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
        }

        // Phase 2: clear both stores, then re-ingest from the prepared chunks (dual write).
        vectorChunkRepo.truncate();
        esService.recreateIndex();

        int chunkCount = 0;
        for (PreparedDoc doc : prepared) {
            indexBuilder.write(doc.chunks(), doc.embeddings());
            chunkCount += doc.chunks().size();
        }

        // Rebuild the pgvector index AFTER data is loaded so IVFFlat can cluster
        // against populated data (better recall than building on an empty table).
        vectorChunkRepo.rebuildIndex(indexType, lists);

        cacheService.clear();
        return new RebuildResult(prepared.size(), chunkCount);
    }

    private ChunkConfig chunkConfigOf(DocumentMeta meta) {
        String splitMode = meta.getSplitMode() != null ? meta.getSplitMode() : ChunkConfig.MODE_SIZE;
        int chunkSize = meta.getChunkSize() != null ? meta.getChunkSize() : ChunkConfig.DEFAULT_CHUNK_SIZE;
        int overlap = meta.getOverlap() != null ? meta.getOverlap() : ChunkConfig.DEFAULT_OVERLAP;
        String delimiter = meta.getDelimiter() != null ? meta.getDelimiter() : "";
        return new ChunkConfig(splitMode, chunkSize, delimiter, overlap);
    }
}
