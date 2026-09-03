package com.rag.eval.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.IndicesStatsResponse;
import co.elastic.clients.elasticsearch.nodes.NodesStatsResponse;
import com.rag.eval.model.ChunkPage;
import com.rag.eval.model.ChunkRecord;
import com.rag.eval.model.EsStatus;
import com.rag.eval.model.OpsStatus;
import com.rag.eval.model.PgStatus;
import com.rag.eval.model.SystemStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
public class OpsService {

    private final ElasticsearchClient esClient;
    private final JdbcTemplate jdbc;
    private final String esIndexName;
    private final ThreadPoolTaskExecutor documentWorkerExecutor;
    private final Semaphore embedSemaphore;
    private final int embedMaxConcurrency;

    public OpsService(ElasticsearchClient esClient,
                      @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbc,
                      @Value("${elasticsearch.index-name}") String esIndexName,
                      @Qualifier("documentWorkerExecutor") ThreadPoolTaskExecutor documentWorkerExecutor,
                      @Qualifier("dashscopeEmbedSemaphore") Semaphore embedSemaphore,
                      @Value("${dashscope.embedding-max-concurrency:2}") int embedMaxConcurrency) {
        this.esClient = esClient;
        this.jdbc = jdbc;
        this.esIndexName = esIndexName;
        this.documentWorkerExecutor = documentWorkerExecutor;
        this.embedSemaphore = embedSemaphore;
        this.embedMaxConcurrency = embedMaxConcurrency;
    }

    public OpsStatus status() {
        return new OpsStatus(esStatus(), pgStatus());
    }

    public SystemStatus systemStatus() {
        return new SystemStatus(workerPool(), jvm(), ingestQueue());
    }

    private SystemStatus.WorkerPool workerPool() {
        var tp = documentWorkerExecutor.getThreadPoolExecutor();
        return new SystemStatus.WorkerPool(
            documentWorkerExecutor.getCorePoolSize(),
            documentWorkerExecutor.getMaxPoolSize(),
            documentWorkerExecutor.getPoolSize(),
            documentWorkerExecutor.getActiveCount(),
            tp.getQueue().size() + tp.getQueue().remainingCapacity(),
            tp.getQueue().size(),
            tp.getCompletedTaskCount(),
            embedMaxConcurrency,
            embedSemaphore.availablePermits()
        );
    }

    private SystemStatus.Jvm jvm() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        long gcCount = 0;
        long gcTimeMs = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += gc.getCollectionCount();
            gcTimeMs += gc.getCollectionTime();
        }
        return new SystemStatus.Jvm(
            heap.getUsed(),
            heap.getMax(),
            nonHeap.getUsed(),
            threads.getThreadCount(),
            classes.getLoadedClassCount(),
            gcCount,
            gcTimeMs,
            runtime.getUptime(),
            os.getSystemLoadAverage(),
            os.getAvailableProcessors()
        );
    }

    private SystemStatus.IngestQueue ingestQueue() {
        long queued = 0, processing = 0, ready = 0, failed = 0;
        for (Map<String, Object> row : jdbc.queryForList(
            "SELECT status, count(*) AS c FROM document_meta GROUP BY status")) {
            long c = ((Number) row.get("c")).longValue();
            switch (String.valueOf(row.get("status"))) {
                case "QUEUED" -> queued = c;
                case "PROCESSING" -> processing = c;
                case "READY" -> ready = c;
                case "FAILED" -> failed = c;
                default -> { }
            }
        }
        return new SystemStatus.IngestQueue(queued, processing, ready, failed);
    }

    private EsStatus esStatus() {
        try {
            HealthResponse health = esClient.cluster().health();
            InfoResponse info = esClient.info();

            double heapUsedPercent = 0.0;
            double cpuPercent = 0.0;
            try {
                NodesStatsResponse nodes = esClient.nodes().stats();
                if (nodes.nodes() != null && !nodes.nodes().isEmpty()) {
                    var node = nodes.nodes().values().iterator().next();
                    if (node.jvm() != null && node.jvm().mem() != null) {
                        heapUsedPercent = node.jvm().mem().heapUsedPercent();
                    }
                    if (node.os() != null && node.os().cpu() != null && node.os().cpu().percent() != null) {
                        cpuPercent = node.os().cpu().percent();
                    }
                }
            } catch (Exception ignored) {
                // 节点统计为可选项，失败不影响其余指标。
            }

            long docCount = 0;
            long storeSizeBytes = 0;
            try {
                IndicesStatsResponse stats = esClient.indices().stats(s -> s.index(esIndexName));
                var all = stats.all();
                if (all != null && all.total() != null) {
                    var total = all.total();
                    if (total.docs() != null) {
                        docCount = total.docs().count();
                    }
                    if (total.store() != null) {
                        storeSizeBytes = total.store().sizeInBytes();
                    }
                }
            } catch (Exception ignored) {
                // 索引可能尚未创建，视为空索引。
            }

            return new EsStatus(
                health.clusterName(),
                info.version() != null ? info.version().number() : null,
                health.status() != null ? health.status().jsonValue() : null,
                health.numberOfNodes(),
                health.numberOfDataNodes(),
                health.activePrimaryShards(),
                health.activeShards(),
                health.relocatingShards(),
                health.unassignedShards(),
                health.numberOfPendingTasks(),
                esIndexName,
                docCount,
                storeSizeBytes,
                heapUsedPercent,
                cpuPercent,
                null
            );
        } catch (Exception e) {
            return new EsStatus(null, null, null, 0, 0, 0, 0, 0, 0, 0, esIndexName, 0, 0, 0.0, 0.0, e.getMessage());
        }
    }

    private PgStatus pgStatus() {
        try {
            String version = jdbc.queryForObject("SELECT version()", String.class);

            Long databaseSizeBytes = jdbc.queryForObject("SELECT pg_database_size(current_database())", Long.class);

            Map<String, Object> dbStats = jdbc.queryForMap(
                "SELECT numbackends, xact_commit, xact_rollback, deadlocks, blks_hit, blks_read " +
                "FROM pg_stat_database WHERE datname = current_database()");
            int numBackends = ((Number) dbStats.get("numbackends")).intValue();
            long xactCommit = ((Number) dbStats.get("xact_commit")).longValue();
            long xactRollback = ((Number) dbStats.get("xact_rollback")).longValue();
            long deadlocks = ((Number) dbStats.get("deadlocks")).longValue();
            long blksHit = ((Number) dbStats.get("blks_hit")).longValue();
            long blksRead = ((Number) dbStats.get("blks_read")).longValue();
            double cacheHitRatio = (blksHit + blksRead) > 0 ? (double) blksHit / (blksHit + blksRead) : 0.0;

            long liveTuples = 0;
            long deadTuples = 0;
            long seqScan = 0;
            long indexScan = 0;
            List<Map<String, Object>> tableRows = jdbc.queryForList(
                "SELECT seq_scan, idx_scan, n_live_tup, n_dead_tup " +
                "FROM pg_stat_user_tables WHERE relname = 'vector_chunks'");
            if (!tableRows.isEmpty()) {
                Map<String, Object> row = tableRows.get(0);
                liveTuples = ((Number) row.get("n_live_tup")).longValue();
                deadTuples = ((Number) row.get("n_dead_tup")).longValue();
                seqScan = ((Number) row.get("seq_scan")).longValue();
                indexScan = ((Number) row.get("idx_scan")).longValue();
            }

            Long chunkCount = jdbc.queryForObject("SELECT count(*) FROM vector_chunks", Long.class);
            Long indexSizeBytes = jdbc.queryForObject("SELECT pg_indexes_size('vector_chunks')", Long.class);

            return new PgStatus(
                version,
                databaseSizeBytes != null ? databaseSizeBytes : 0,
                numBackends,
                xactCommit,
                xactRollback,
                deadlocks,
                cacheHitRatio,
                "vector_chunks",
                liveTuples,
                deadTuples,
                seqScan,
                indexScan,
                chunkCount != null ? chunkCount : 0,
                indexSizeBytes != null ? indexSizeBytes : 0,
                null
            );
        } catch (Exception e) {
            return new PgStatus(null, 0, 0, 0, 0, 0, 0.0, "vector_chunks", 0, 0, 0, 0, 0, 0, e.getMessage());
        }
    }

    public ChunkPage chunks(String backend, String fileName, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        if ("es".equalsIgnoreCase(backend)) {
            return esChunks(fileName, safePage, safeSize);
        }
        return pgChunks(fileName, safePage, safeSize);
    }

    private ChunkPage pgChunks(String fileName, int page, int size) {
        String where = (fileName != null && !fileName.isBlank()) ? " WHERE file_name = ?" : "";
        List<Object> params = new ArrayList<>();
        if (fileName != null && !fileName.isBlank()) {
            params.add(fileName);
        }
        Object[] countArgs = params.toArray();
        long total = jdbc.queryForObject("SELECT count(*) FROM vector_chunks" + where, Long.class, countArgs);

        String sql = "SELECT chunk_id, file_name, chapter, section, chunk_index, content " +
            "FROM vector_chunks" + where + " ORDER BY chunk_index LIMIT ? OFFSET ?";
        params.add(size);
        params.add((page - 1) * size);
        List<ChunkRecord> items = jdbc.query(sql, (rs, i) -> new ChunkRecord(
            rs.getString("chunk_id"),
            rs.getString("file_name"),
            rs.getString("chapter"),
            rs.getString("section"),
            rs.getInt("chunk_index"),
            rs.getString("content")
        ), params.toArray());
        return new ChunkPage(total, items);
    }

    private ChunkPage esChunks(String fileName, int page, int size) {
        try {
            Query query = (fileName != null && !fileName.isBlank())
                ? Query.of(q -> q.term(t -> t.field("file_name.keyword").value(fileName)))
                : Query.of(q -> q.matchAll(m -> m));

            long total = esClient.count(c -> c.index(esIndexName).query(query)).count();

            SearchRequest request = SearchRequest.of(s -> s
                .index(esIndexName)
                .query(query)
                .from((page - 1) * size)
                .size(size));
            SearchResponse<Map> response = esClient.search(request, Map.class);

            List<ChunkRecord> items = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                Map<String, Object> src = hit.source();
                if (src == null) continue;
                items.add(new ChunkRecord(
                    (String) src.get("chunk_id"),
                    (String) src.get("file_name"),
                    (String) src.get("chapter"),
                    (String) src.get("section"),
                    null,
                    (String) src.get("content")
                ));
            }
            return new ChunkPage(total, items);
        } catch (Exception e) {
            throw new RuntimeException("ES 数据浏览失败: " + e.getMessage(), e);
        }
    }
}
