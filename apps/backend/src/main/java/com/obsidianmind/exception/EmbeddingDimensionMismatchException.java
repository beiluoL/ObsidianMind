package com.obsidianmind.exception;

/**
 * Query/文档向量维度与 Collection 配置不一致（对应 HTTP 500）：
 * 通常是换了 Embedding 模型但未同步 milvus.vector-dimension，属配置矛盾而非服务不可用。
 */
public class EmbeddingDimensionMismatchException extends BusinessException {
    public EmbeddingDimensionMismatchException(String message) {
        super("EMBEDDING_DIMENSION_MISMATCH", message);
    }
}
