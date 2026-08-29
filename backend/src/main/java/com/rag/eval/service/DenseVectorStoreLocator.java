package com.rag.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves the configured dense-vector backend, falling back to pgvector when the
 * configured backend is unknown or unavailable.
 */
@Component
public class DenseVectorStoreLocator {

    private static final Logger log = LoggerFactory.getLogger(DenseVectorStoreLocator.class);

    private final Map<String, DenseVectorStore> stores;
    private final ConfigService config;

    public DenseVectorStoreLocator(Map<String, DenseVectorStore> stores, ConfigService config) {
        this.stores = stores;
        this.config = config;
    }

    public DenseVectorStore resolve() {
        String backend = config.get("vector.backend", "pgvector");
        DenseVectorStore store = stores.get(backend);
        if (store == null) {
            log.warn("Unknown vector backend '{}', falling back to pgvector", backend);
            store = stores.get("pgvector");
        }
        return store;
    }
}
