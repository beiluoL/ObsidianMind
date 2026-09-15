package com.obsidianmind.exception;

/**
 * RAG 管线通用业务异常（Phase 5）：携带语义化 code，主要用于 SSE error 事件
 * （RETRIEVAL_ERROR / CONTEXT_BUILD_ERROR / LLM_STREAM_ERROR / CLIENT_DISCONNECTED 等）。
 * 语义明确的场景优先使用专属异常（如 LlmUnavailableException / LlmTimeoutException）。
 */
public class RagPipelineException extends BusinessException {
    public RagPipelineException(String code, String message) {
        super(code, message);
    }
}
