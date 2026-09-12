package com.obsidianmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 运行时配置：provider 可切换（ollama / 未来 openai / deepseek 等 OpenAI Compatible API）。
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(String provider, Ollama ollama) {

    public record Ollama(String baseUrl, String chatModel, String embeddingModel, int timeoutSeconds) {
    }
}
