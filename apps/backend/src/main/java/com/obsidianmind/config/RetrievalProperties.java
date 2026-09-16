package com.obsidianmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 混合检索配置（Phase 6）。独立于 ai.retrieval.*（既有语义检索参数），前缀 retrieval.*。
 *
 * 命名与 docs/architecture/hybrid-retrieval.md 对齐；默认值依据：
 * - rrf.k=60：RRF 原始论文（Cormack et al., 2009）与业界通用取值，平滑排名差异；
 * - keyword.k1=1.5 / b=0.75：Okapi BM25 经典默认参数；
 * - reranker 默认关闭：本地无神经 Reranker 模型，诚实 NOT_CONFIGURED，不假装接入。
 */
@ConfigurationProperties(prefix = "retrieval")
public record RetrievalProperties(
        String defaultMode,
        Rrf rrf,
        Candidates candidates,
        Keyword keyword,
        Reranker reranker,
        @DefaultValue("false") boolean debugEnabled) {

    /** RRF 融合参数：k 越大，头部排名的增益越平滑（公式见 docs/architecture/rrf.md）。 */
    public record Rrf(@DefaultValue("60") int k) {
    }

    /** 两路召回的候选数：RRF 前各自取回的 TopN（最终结果仍由 ai.retrieval.top-k 裁剪）。 */
    public record Candidates(
            @DefaultValue("20") int vector,
            @DefaultValue("20") int keyword) {
    }

    /** 关键词检索（BM25）参数。 */
    public record Keyword(
            @DefaultValue("1.5") double k1,
            @DefaultValue("0.75") double b) {
    }

    /**
     * Reranker 配置。enabled=false 时 NOT_CONFIGURED：直接沿用 RRF 排序，绝不假装二次排序。
     * provider/model 为未来接入真实模型预留（当前唯一实现是确定性 Lexical Reranker）。
     */
    public record Reranker(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("lexical") String provider,
            @DefaultValue("") String model,
            @DefaultValue("20") int topN) {
    }

    public String defaultModeOrDefault() {
        return defaultMode != null && !defaultMode.isBlank() ? defaultMode : "HYBRID";
    }

    public Rrf rrfOrDefault() {
        return rrf != null ? rrf : new Rrf(60);
    }

    public Candidates candidatesOrDefault() {
        return candidates != null ? candidates : new Candidates(20, 20);
    }

    public Keyword keywordOrDefault() {
        return keyword != null ? keyword : new Keyword(1.5, 0.75);
    }

    public Reranker rerankerOrDefault() {
        return reranker != null ? reranker : new Reranker(false, "lexical", "", 20);
    }
}
