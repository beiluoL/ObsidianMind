package com.obsidianmind.exception;

/**
 * 向量库操作失败（对应 HTTP 503）：集合创建、写入、删除、查询等操作级错误。
 * 连接级不可用仍用 MilvusUnavailableException。
 */
public class VectorStoreException extends BusinessException {
    public VectorStoreException(String message) {
        super("VECTOR_STORE_ERROR", message);
    }
}
