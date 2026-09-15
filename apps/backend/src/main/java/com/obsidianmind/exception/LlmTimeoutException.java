package com.obsidianmind.exception;

/**
 * LLM 生成超时（Phase 5）：流式读超时（首 token 或 token 间隔超过 ai.rag.llm-timeout-seconds）。
 */
public class LlmTimeoutException extends BusinessException {
    public LlmTimeoutException(String message) {
        super("LLM_TIMEOUT", message);
    }
}
