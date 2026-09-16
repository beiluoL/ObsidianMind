package com.obsidianmind.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 混合检索请求（Phase 6）。mode 可选（VECTOR / KEYWORD / HYBRID，不传 = 服务端默认 HYBRID）；
 * 不暴露 RRF k / BM25 参数 / Reranker 模型名等底层配置——那些属于 Advanced Configuration（服务端配置）。
 */
@Schema(description = "混合检索请求")
public record HybridSearchRequest(
        @Schema(description = "用户查询", requiredMode = Schema.RequiredMode.REQUIRED) String query,
        @Schema(description = "检索模式：VECTOR / KEYWORD / HYBRID（默认 HYBRID）") String mode,
        @Schema(description = "返回条数（默认 5，上限 20）") Integer topK) {

    /** mode 归一化：null/空白 → 默认模式（由服务端配置决定）。 */
    public String modeOrDefault() {
        return mode == null || mode.isBlank() ? null : mode;
    }
}
