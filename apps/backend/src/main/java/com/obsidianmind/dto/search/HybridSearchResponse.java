package com.obsidianmind.dto.search;

import com.obsidianmind.retrieval.RetrievalMode;
import com.obsidianmind.service.RetrievalService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 混合检索响应（Phase 6）。
 * sources 与 Phase 4 语义检索 Source 形状完全兼容（title/path/heading/snippet/score）；
 * 新增字段全部是"可观察性"而非技术参数：实际生效模式、降级记录、Reranker 状态。
 * score 语义 = finalScore（Rerank > RRF > 单路原始分），分数体系随模式而异，仅供排序参考。
 */
@Schema(description = "混合检索响应")
public record HybridSearchResponse(
        @Schema(description = "规范化后的查询") String query,
        @Schema(description = "请求的检索模式") RetrievalMode requestedMode,
        @Schema(description = "实际生效模式（降级后可能不同）") RetrievalMode effectiveMode,
        @Schema(description = "降级记录（空 = 无降级），如 VECTOR_FALLBACK_TO_KEYWORD") List<String> fallbacks,
        @Schema(description = "Reranker 状态：APPLIED / NOT_CONFIGURED / SKIPPED_EMPTY") String rerankerStatus,
        @Schema(description = "检索耗时（毫秒）") long elapsedMs,
        @Schema(description = "结果 Sources（与语义检索 Source 形状兼容）") List<RetrievalService.Source> sources) {
}
