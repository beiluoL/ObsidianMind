package com.obsidianmind.retrieval;

import java.util.List;

/**
 * 检索 Provider 抽象（Phase 6）：RetrievalService / HybridRetriever 只认识本接口，
 * 不直接知道 Milvus / BM25 / Lucene——换实现或加实现（如未来 Lucene/FTS5）上层不动。
 *
 * 契约：
 * - 实现返回按相关性降序的候选列表（rank = 位置 + 1）；
 * - 候选必须携带可还原 Source 的完整元数据（title/path/heading/content/documentId/chunkIndex）；
 * - 失败抛 BusinessException 语义化异常（如 MILVUS_UNAVAILABLE），由 HybridRetriever 决定降级路径；
 * - isAvailable() 是廉价静态检查（配置/连接状态），不发起网络调用；真正的失败在 retrieve 时暴露。
 */
public interface RetrievalProvider {

    /** Provider 名称（日志与 trace 用），如 "vector" / "keyword"。 */
    String name();

    /** 廉价可用性检查：不发起网络调用，只判断依赖是否就绪（如 Vault 已连接）。 */
    boolean isAvailable();

    /**
     * 检索：Query → 候选列表（降序）。
     *
     * @param query          已校验非空的用户查询
     * @param candidateCount 期望召回的候选数（Provider 内部可夹取）
     */
    List<RetrievalCandidate> retrieve(String query, int candidateCount);
}
