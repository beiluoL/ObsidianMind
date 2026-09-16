package com.obsidianmind.modelcenter;

import com.obsidianmind.service.LLMService;
import com.obsidianmind.service.LLMService.LlmStreamListener;

/**
 * Ollama 适配器（Phase 5.5）：委托既有 OllamaLLMService（Phase 5 已验证的 NDJSON 流式实现）。
 * ModelRouter 对 OLLAMA 类型模型返回本适配器；无默认模型时也回退到它（legacy 兼容路径）。
 */
public class OllamaChatModelAdapter implements ChatModelAdapter {

    private final LLMService delegate;
    private final String fingerprint;

    public OllamaChatModelAdapter(LLMService delegate, String fingerprint) {
        this.delegate = delegate;
        this.fingerprint = fingerprint;
    }

    @Override
    public String complete(String systemPrompt, String userMessage, ChatOptions options) {
        return delegate.complete(systemPrompt, userMessage);
    }

    @Override
    public void streamComplete(String systemPrompt, String userMessage, ChatOptions options,
                               LlmStreamListener listener) {
        delegate.streamComplete(systemPrompt, userMessage, listener);
    }

    @Override
    public String fingerprint() {
        return fingerprint;
    }
}
