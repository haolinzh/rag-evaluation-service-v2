package com.rag.eval.model;

public record SystemStatus(WorkerPool workerPool, Jvm jvm, IngestQueue ingestQueue) {

    public record WorkerPool(
        int coreSize,
        int maxSize,
        int poolSize,
        int activeThreads,
        int queueCapacity,
        int queueSize,
        long completedTasks,
        int embedPermits,
        int embedAvailable
    ) {}

    public record Jvm(
        long heapUsed,
        long heapMax,
        long nonHeapUsed,
        int threadCount,
        long loadedClassCount,
        long gcCount,
        long gcTimeMs,
        long uptimeMs,
        double systemLoadAvg,
        int availableProcessors
    ) {}

    public record IngestQueue(long queued, long processing, long ready, long failed) {}
}
