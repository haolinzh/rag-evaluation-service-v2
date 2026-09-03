package com.rag.eval.service;

import com.rag.eval.model.OpsMetrics;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class MetricsCollector {

    public OpsMetrics startRequest(String sessionId, String retrievalMode) {
        OpsMetrics m = new OpsMetrics();
        // 复用 HTTP 层 TraceIdFilter 写入的 traceId，使 requestId 与日志/响应头一致；
        // 异步 worker 线程 MDC 为空时兜底新生成。
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        m.setRequestId(traceId);
        m.setSessionId(sessionId);
        m.setTimestamp(Instant.now());
        m.setRetrievalMode(retrievalMode);
        return m;
    }

    public void complete(OpsMetrics m) {
        // Metrics are persisted to request_log by ChatService.logRequest; nothing in-memory to update.
    }
}
