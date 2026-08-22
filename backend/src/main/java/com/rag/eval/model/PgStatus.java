package com.rag.eval.model;

public record PgStatus(
    String version,
    long databaseSizeBytes,
    int numBackends,
    long xactCommit,
    long xactRollback,
    long deadlocks,
    double cacheHitRatio,
    String tableName,
    long liveTuples,
    long deadTuples,
    long seqScan,
    long indexScan,
    long chunkCount,
    long indexSizeBytes,
    String error
) {}
