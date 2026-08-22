package com.rag.eval.model;

import java.util.List;

public record ChunkPage(long total, List<ChunkRecord> items) {}
