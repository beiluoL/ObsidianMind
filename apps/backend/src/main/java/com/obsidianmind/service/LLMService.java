package com.obsidianmind.service;

/**
 * LLM 抽象 —— 业务代码禁止直接依赖 Ollama 客户端。
 * 未来可切换 OpenAI / DeepSeek / Qwen 等 OpenAI Compatible Provider。
 */
public interface LLMService {

    String provider();

    boolean isAvailable();

    /** 生成补全。Provider 不可用时抛 OllamaUnavailableException（由实现决定）。 */
    String complete(String systemPrompt, String userMessage);
}
