package com.rag.eval.service;

import com.rag.eval.repository.DocumentMetaRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DocumentIngestPoller {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestPoller.class);

    private static final int BATCH_SIZE = 10;
    private static final long STALE_TIMEOUT_MINUTES = 30;

    private final DocumentMetaRepo repo;
    private final DocumentIngestWorker worker;
    private final IngestScheduler gate;
    private final ThreadPoolTaskExecutor executor;

    public DocumentIngestPoller(DocumentMetaRepo repo, DocumentIngestWorker worker,
                                IngestScheduler gate,
                                @Qualifier("documentWorkerExecutor") ThreadPoolTaskExecutor executor) {
        this.repo = repo;
        this.worker = worker;
        this.gate = gate;
        this.executor = executor;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 3000)
    public void poll() {
        if (gate.isPaused()) {
            return;
        }
        int headroom = executor.getThreadPoolExecutor().getQueue().remainingCapacity();
        if (headroom <= 0) {
            return;
        }
        int batch = Math.min(headroom, BATCH_SIZE);
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = repo.findQueuedIds(now, PageRequest.of(0, batch));
        for (Long id : ids) {
            if (repo.claim(id, now) == 1) {
                executor.execute(() -> worker.process(id));
            }
        }
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void resetStale() {
        if (gate.isPaused()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(STALE_TIMEOUT_MINUTES);
        int n = repo.resetStaleProcessing(now, cutoff);
        if (n > 0) {
            log.info("Reset {} stale PROCESSING document(s) to QUEUED", n);
        }
    }
}
