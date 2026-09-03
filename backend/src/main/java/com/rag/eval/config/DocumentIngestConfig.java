package com.rag.eval.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableScheduling
public class DocumentIngestConfig {

    @Bean("documentWorkerExecutor")
    public ThreadPoolTaskExecutor documentWorkerExecutor(
            @Value("${document.ingest.worker-count:3}") int workerCount,
            @Value("${document.ingest.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workerCount);
        executor.setMaxPoolSize(workerCount);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("doc-ingest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean("dashscopeEmbedSemaphore")
    public Semaphore dashscopeEmbedSemaphore(@Value("${dashscope.embedding-max-concurrency:2}") int permits) {
        return new Semaphore(permits);
    }
}
