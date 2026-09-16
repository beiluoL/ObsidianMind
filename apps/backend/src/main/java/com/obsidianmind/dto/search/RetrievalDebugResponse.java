package com.obsidianmind.dto.search;

import com.obsidianmind.retrieval.HybridRetriever;
import com.obsidianmind.retrieval.RetrievalCandidate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Retrieval Debug 响应（Phase 6，仅 debug-enabled 时暴露）。
 *
 * 为什么独立 DTO 而不复用 RetrievalOutcome：分数通道用 NaN 表达"该路未产出"，
 * 但 NaN 不是合法 JSON（前端 JSON.parse 直接失败）——DTO 层统一转 null。
 * 仅开发/调试使用：普通用户界面绝不渲染本响应。
 */
@Schema(description = "检索 Debug 全链路 trace（仅开发模式）")
public record RetrievalDebugResponse(
        @Schema(description = "请求的检索模式") String requestedMode,
        @Schema(description = "实际生效模式") String effectiveMode,
        @Schema(description = "降级记录") List<String> fallbacks,
        @Schema(description = "Reranker 状态") String rerankerStatus,
        @Schema(description = "向量路 Top 候选") List<TraceItem> vector,
        @Schema(description = "关键词路 Top 候选") List<TraceItem> keyword,
        @Schema(description = "RRF 融合后") List<TraceItem> fused,
        @Schema(description = "最终结果") List<TraceItem> finalResults,
        @Schema(description = "各阶段耗时（毫秒）") Timing timing) {

    public static RetrievalDebugResponse of(HybridRetriever.RetrievalOutcome outcome) {
        return new RetrievalDebugResponse(
                outcome.requestedMode().name(),
                outcome.effectiveMode().name(),
                outcome.fallbacks(),
                outcome.rerankerStatus(),
                trace(outcome.vectorCandidates()),
                trace(outcome.keywordCandidates()),
                trace(outcome.fusedCandidates()),
                trace(outcome.finalCandidates()),
                new Timing(outcome.vectorMs(), outcome.keywordMs(), outcome.fusionMs(),
                        outcome.rerankerMs(), outcome.totalMs()));
    }

    private static List<TraceItem> trace(List<RetrievalCandidate> candidates) {
        List<TraceItem> items = new java.util.ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            RetrievalCandidate c = candidates.get(i);
            items.add(new TraceItem(i + 1, c.chunkId(), c.documentId(), c.title(), c.heading(),
                    boxed(c.vectorScore()), boxed(c.keywordScore()), boxed(c.rrfScore()),
                    boxed(c.rerankScore()), boxed(c.finalScore())));
        }
        return List.copyOf(items);
    }

    private static Double boxed(double score) {
        return Double.isNaN(score) ? null : score;
    }

    @Schema(description = "单条候选的各通道分数（null = 该通道未产出）")
    public record TraceItem(
            int rank,
            String chunkId,
            String documentId,
            String title,
            String heading,
            Double vectorScore,
            Double keywordScore,
            Double rrfScore,
            Double rerankScore,
            Double finalScore) {
    }

    public record Timing(
            long vectorMs,
            long keywordMs,
            long fusionMs,
            long rerankerMs,
            long totalMs) {
    }
}
