package com.obsidianmind.domain;

/**
 * AI 回答引用来源（未来 RAG：由向量检索产生的 chunk 级引用）。
 */
public record Source(
        String noteId,
        String title,
        String chunkId,
        double score
) {
}
