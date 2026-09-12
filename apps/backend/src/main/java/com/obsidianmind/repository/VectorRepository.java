package com.obsidianmind.repository;

import com.obsidianmind.domain.Chunk;
import com.obsidianmind.domain.Embedding;

import java.util.List;

/**
 * 向量仓储抽象 —— 未来由 MilvusVectorRepository 实现。
 * 现在只定义契约，Phase 1 不提供任何实现（不伪造向量检索）。
 */
public interface VectorRepository {

    void save(String collection, List<Chunk> chunks, List<Embedding> embeddings);

    List<SearchResultRecord> search(String collection, float[] queryVector, int topK);

    void deleteByNoteId(String collection, String noteId);

    /**
     * 向量检索结果记录。
     */
    record SearchResultRecord(String chunkId, String noteId, double score) {
    }
}
