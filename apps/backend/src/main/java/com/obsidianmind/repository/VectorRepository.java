package com.obsidianmind.repository;

import com.obsidianmind.domain.Chunk;

import java.util.List;
import java.util.Map;

/**
 * 向量仓储抽象（Phase 3 知识管线）。
 *
 * 实现方：{@link MilvusVectorStore}（生产，Milvus SDK）、InMemoryVectorStore（测试专用，非 Spring Bean）。
 * 契约与设计依据见 docs/architecture/milvus.md。
 *
 * 不变量：
 * - upsert 是文档级 replace（先删后插），保证文档重切块后不留幽灵旧 Chunk；
 * - contentHash 为文档级哈希（同文档所有 Chunk 相同），增量索引的判定依据；
 * - search 结果必须携带足以还原 Source 的元数据（relativePath/title/headingPath/chunkIndex）。
 */
public interface VectorRepository {

    /** 惰性确保 Collection 存在（建集合 + 建向量索引 + load）。已存在且维度一致时为无操作。 */
    void ensureCollection(String collection, int dimension);

    /** 文档级 upsert：写入一组 Chunk 与一一对应的向量（顺序一致）。 */
    void upsert(String collection, List<Chunk> chunks, List<float[]> vectors);

    /** 删除一组文档的全部向量（文档更新 / 删除后的清理）。vaultId 限定删除边界，防止误删其他 Vault 的同名路径。 */
    void deleteByDocuments(String collection, String vaultId, List<String> documentIds);

    /** 存量索引的 documentId → contentHash 映射（增量索引对比用），仅限指定 Vault。 */
    Map<String, String> loadDocumentHashes(String collection, String vaultId);

    /** 向量相似度检索，返回按相似度降序的 topK 条（带 Source 元数据）；vaultId 为检索边界，越界数据不可见。 */
    List<SearchResultRecord> search(String collection, String vaultId, float[] queryVector, int topK);

    /** Collection 内向量总数（验证与可观测）。 */
    long count(String collection);

    /** 检索结果记录：score + 还原 Source 所需的全部元数据。 */
    record SearchResultRecord(
            String chunkId,
            String documentId,
            String relativePath,
            String title,
            String headingPath,
            int chunkIndex,
            String content,
            double score) {
    }
}
