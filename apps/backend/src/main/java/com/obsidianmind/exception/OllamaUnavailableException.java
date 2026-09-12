package com.obsidianmind.exception;

public class OllamaUnavailableException extends BusinessException {
    public OllamaUnavailableException(String message) {
        super("OLLAMA_UNAVAILABLE", message);
    }
}
