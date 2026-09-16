package com.obsidianmind.exception;

/**
 * 检索整体不可用（对应 HTTP 503）：VECTOR 与 KEYWORD 两路全部失败时抛出。
 * 单路失败会先走降级（可观察，见 HybridRetriever fallback 日志与响应字段），全灭才到本异常。
 */
public class RetrievalUnavailableException extends BusinessException {
    public RetrievalUnavailableException(String message) {
        super("RETRIEVAL_UNAVAILABLE", message);
    }
}
