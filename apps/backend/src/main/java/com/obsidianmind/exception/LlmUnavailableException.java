package com.obsidianmind.exception;

/**
 * LLM 生成阶段不可用（Phase 5 RAG Chat）：与检索阶段的 OLLAMA_UNAVAILABLE 区分开——
 * Chat 链路中 LLM 失败是「回答不可用」，Embedding 失败是「检索不可用」，前端据此区分提示。
 */
public class LlmUnavailableException extends BusinessException {
    public LlmUnavailableException(String message) {
        super("LLM_UNAVAILABLE", message);
    }
}
