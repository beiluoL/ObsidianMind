package com.obsidianmind.retrieval;

/**
 * 检索候选（Phase 6 统一形状）：两路 Provider 与 RRF / Reranker 的通用交换模型。
 *
 * 分数语义（绝不混淆——debug 与评估都依赖这三个分数独立存在）：
 * - vectorScore：向量相似度（COSINE，0~1）；未参与向量召回时为 NaN；
 * - keywordScore：BM25 分（无上界）；未参与关键词召回时为 NaN；
 * - rrfScore：Reciprocal Rank Fusion 融合分（量级 ~1/(k+rank)，k 默认 60）；
 * - rerankScore：Reranker 二次排序分（0~1）；未启用或未命中 Reranker TopN 时为 NaN。
 *
 * NaN 而非 0：0 是合法分数（如 BM25 无命中），NaN 明确表达"该路没有产出这个分数"。
 * finalScore 由 HybridRetriever 计算：reranked ? rerankScore : (fused ? rrfScore : 唯一路原始分)。
 */
public record RetrievalCandidate(
        String chunkId,
        String documentId,
        String path,
        String title,
        String heading,
        int chunkIndex,
        String content,
        double vectorScore,
        double keywordScore,
        double rrfScore,
        double rerankScore,
        RetrievalType retrievalType) {

    /** 候选来源类型：单路命中原路；两路同时命中为 HYBRID（RRF 融合后设置）。 */
    public enum RetrievalType {
        VECTOR,
        KEYWORD,
        HYBRID
    }

    /** 该候选的最终排序分（HybridRetriever 裁剪 TopK 前统一写入 rerankScore 或 rrfScore 通道）。 */
    public double finalScore() {
        if (!Double.isNaN(rerankScore)) {
            return rerankScore;
        }
        if (!Double.isNaN(rrfScore)) {
            return rrfScore;
        }
        if (!Double.isNaN(vectorScore)) {
            return vectorScore;
        }
        return Double.isNaN(keywordScore) ? 0 : keywordScore;
    }

    /** 身份字段（chunkId 唯一）；RRF 与去重按此合并。 */
    public String identity() {
        return chunkId;
    }

    /** 基于另一路候选合并分数（身份字段取本候选；缺失分数用 other 补齐）；来源类型升级为 HYBRID。 */
    public RetrievalCandidate mergeFrom(RetrievalCandidate other) {
        return new RetrievalCandidate(
                chunkId, documentId, path, title, heading, chunkIndex, content,
                Double.isNaN(vectorScore) ? other.vectorScore : vectorScore,
                Double.isNaN(keywordScore) ? other.keywordScore : keywordScore,
                rrfScore,
                rerankScore,
                RetrievalType.HYBRID);
    }

    /** 生成携带新分数的副本（RRF / Reranker 阶段使用，身份字段不变）。 */
    public RetrievalCandidate withScores(double rrfScore, double rerankScore, RetrievalType type) {
        return new RetrievalCandidate(chunkId, documentId, path, title, heading, chunkIndex, content,
                vectorScore, keywordScore, rrfScore, rerankScore, type);
    }
}
