package com.obsidianmind.retrieval;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion（Phase 6）。
 *
 * 公式：RRF(d) = Σ_lists 1 / (k + rank_list(d))，rank 从 1 计。
 * k 默认 60（Cormack et al. 2009 原始论文取值）：平滑头部排名差异，避免 rank1 独裁。
 *
 * 为什么融合 rank 而不是原始分：向量 COSINE 分（0~1）与 BM25 分（无上界）不可比，
 * 直接相加 = 语义错误。RRF 只消费"排名"这一种各系统通用的信息。
 *
 * 语义保证：
 * - 只在两路都召回的候选上叠加贡献；单路候选保留原始分数（vectorScore / keywordScore 不丢）；
 * - 两路同时命中 → retrievalType 升级为 HYBRID；
 * - 输入列表顺序即 rank 顺序（Provider 契约保证降序），本类不重排输入。
 */
public final class ReciprocalRankFusion {

    private ReciprocalRankFusion() {
    }

    /**
     * 融合多个 ranked 候选列表。
     *
     * @param rankedLists 各路检索结果（每个列表内部已按相关性降序）
     * @param k           RRF 平滑常数（> 0）
     * @return 按 rrfScore 降序的融合候选（保留各路原始分数）
     */
    public static List<RetrievalCandidate> fuse(List<List<RetrievalCandidate>> rankedLists, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("RRF k 必须为正数，当前: " + k);
        }
        Map<String, RetrievalCandidate> merged = new LinkedHashMap<>();
        for (List<RetrievalCandidate> list : rankedLists) {
            if (list == null) {
                continue;
            }
            for (int rank = 0; rank < list.size(); rank++) {
                RetrievalCandidate candidate = list.get(rank);
                double contribution = 1.0 / (k + rank + 1);
                RetrievalCandidate existing = merged.get(candidate.identity());
                if (existing == null) {
                    merged.put(candidate.identity(), candidate.withScores(contribution, Double.NaN,
                            candidate.retrievalType()));
                } else {
                    RetrievalCandidate combined = existing.mergeFrom(candidate);
                    merged.put(candidate.identity(), combined.withScores(existing.rrfScore() + contribution,
                            Double.NaN, RetrievalCandidate.RetrievalType.HYBRID));
                }
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(RetrievalCandidate::rrfScore).reversed())
                .toList();
    }
}
