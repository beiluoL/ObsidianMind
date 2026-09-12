package com.obsidianmind.repository;

import com.obsidianmind.domain.Chunk;
import com.obsidianmind.domain.Embedding;

import java.util.List;

/**
 * 向量仓储抽象 —— 未来由 MilvusVectorRepository 实现。
 * 现在只定义契约，Phase 1 不提供任何实现（不伪造向量检索）。
 */
public interface VectorRepository {

    /** 批量写入 Chunk 与对应向量（幂等：同 noteId 重复写入按实现清理）。 */
    void save(String collection, List<Chunk> chunks, List<Embedding> embeddings);

    /** 向量相似度检索，返回按 score 降序的 topK 条记录。 */
    List<SearchResultRecord> search(String collection, float[] queryVector, int topK);

    /** 删除指定笔记的全部向量（笔记更新/删除后重建索引前调用）。 */
    void deleteByNoteId(String collection, String noteId);

    /**
     * 向量检索结果记录。
     */
    record SearchResultRecord(String chunkId, String noteId, double score) {
    }
}
