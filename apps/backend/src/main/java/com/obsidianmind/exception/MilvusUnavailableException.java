package com.obsidianmind.exception;

public class MilvusUnavailableException extends BusinessException {
    public MilvusUnavailableException(String message) {
        super("MILVUS_UNAVAILABLE", message);
    }
}
