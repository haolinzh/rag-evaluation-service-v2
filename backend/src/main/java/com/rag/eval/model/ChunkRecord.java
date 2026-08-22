package com.rag.eval.model;

public record ChunkRecord(
    String chunkId,
    String fileName,
    String chapter,
    String section,
    Integer chunkIndex,
    String content
) {}
