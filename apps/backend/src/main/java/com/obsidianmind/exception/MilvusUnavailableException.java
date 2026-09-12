package com.obsidianmind.exception;

/**
 * Milvus 向量库不可用（对应 HTTP 503）：未启动、连接超时或集合操作失败时抛出。
 */
public class MilvusUnavailableException extends BusinessException {
    public MilvusUnavailableException(String message) {
        super("MILVUS_UNAVAILABLE", message);
    }
}
