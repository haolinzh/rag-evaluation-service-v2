package com.rag.eval.model;

public record EsStatus(
    String clusterName,
    String version,
    String status,
    int nodeCount,
    int dataNodeCount,
    int activePrimaryShards,
    int activeShards,
    int relocatingShards,
    int unassignedShards,
    int pendingTasks,
    String indexName,
    long docCount,
    long storeSizeBytes,
    double heapUsedPercent,
    double cpuPercent,
    String error
) {}
