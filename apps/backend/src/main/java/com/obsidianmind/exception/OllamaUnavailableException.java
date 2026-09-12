package com.obsidianmind.exception;

/**
 * Ollama 服务不可用（对应 HTTP 503）：未启动、模型未加载或请求超时时抛出。
 */
public class OllamaUnavailableException extends BusinessException {
    public OllamaUnavailableException(String message) {
        super("OLLAMA_UNAVAILABLE", message);
    }
}
