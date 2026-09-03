package com.rag.eval.model;

/** 永久性入库错误（文件缺失、解析失败、embedding 契约错误）：不重试，直接终态 FAILED。 */
public class DocumentIngestPermanentException extends RuntimeException {
    public DocumentIngestPermanentException(String message) {
        super(message);
    }
}
