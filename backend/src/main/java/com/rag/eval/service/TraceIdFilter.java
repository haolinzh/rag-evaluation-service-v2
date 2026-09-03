package com.rag.eval.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** 为每个 HTTP 请求生成/透传 traceId，写入 MDC 与响应头，供日志与落库贯穿。 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
