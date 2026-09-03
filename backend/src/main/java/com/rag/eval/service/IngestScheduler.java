package com.rag.eval.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** 入库 poller 的暂停闸：全量重建（RebuildService）期间暂停 poller，避免互相踩。 */
@Component
public class IngestScheduler {

    private final AtomicBoolean paused = new AtomicBoolean(false);

    public boolean isPaused() {
        return paused.get();
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }
}
