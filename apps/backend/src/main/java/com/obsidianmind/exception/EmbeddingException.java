package com.obsidianmind.exception;

/**
 * Embedding 失败（对应 HTTP 503）：模型缺失、响应畸形、向量维度不符等。
 * 与 OllamaUnavailableException 的区别：服务可达但结果不可用。
 */
public class EmbeddingException extends BusinessException {
    public EmbeddingException(String message) {
        super("EMBEDDING_ERROR", message);
    }
}
