package com.obsidianmind.dto.search;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.RetrievalProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 检索配置视图（GET /api/v1/search/config，Phase 6 Advanced Retrieval / Debug 面板数据源）。
 * 只读视图：普通用户界面默认折叠；不包含任何凭据与内部连接信息。
 */
@Schema(description = "检索配置（只读）")
public record RetrievalConfigResponse(
        @Schema(description = "默认检索模式") String defaultMode,
        @Schema(description = "默认返回条数") int defaultTopK,
        @Schema(description = "返回条数上限") int maxTopK,
        @Schema(description = "向量路候选数") int vectorCandidates,
        @Schema(description = "关键词路候选数") int keywordCandidates,
        @Schema(description = "RRF 平滑常数 k") int rrfK,
        @Schema(description = "同文档多样性上限") int maxPerDocument,
        @Schema(description = "相似度阈值（VECTOR 模式，0 = 关闭）") double scoreThreshold,
        @Schema(description = "Reranker 是否启用") boolean rerankerEnabled,
        @Schema(description = "Reranker 候选数") int rerankerTopN,
        @Schema(description = "Retrieval Debug 是否可用") boolean debugEnabled) {

    public static RetrievalConfigResponse of(AiProperties ai, RetrievalProperties props) {
        AiProperties.Retrieval cfg = ai.retrievalOrDefault();
        RetrievalProperties.Reranker reranker = props.rerankerOrDefault();
        RetrievalProperties.Candidates candidates = props.candidatesOrDefault();
        return new RetrievalConfigResponse(
                props.defaultModeOrDefault(),
                cfg.defaultTopK(),
                cfg.maxTopK(),
                candidates.vector(),
                candidates.keyword(),
                props.rrfOrDefault().k(),
                cfg.maxPerDocument(),
                cfg.scoreThreshold(),
                reranker.enabled(),
                reranker.topN(),
                props.debugEnabled());
    }
}
