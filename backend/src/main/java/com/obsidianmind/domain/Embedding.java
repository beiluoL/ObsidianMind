package com.obsidianmind.domain;

/**
 * 未来 RAG 数据模型：Chunk 经 EmbeddingService 生成的向量。
 */
public record Embedding(
        String chunkId,
        float[] vector
) {
}
