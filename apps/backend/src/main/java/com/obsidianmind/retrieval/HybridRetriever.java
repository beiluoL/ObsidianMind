package com.obsidianmind.retrieval;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.RetrievalProperties;
import com.obsidianmind.exception.RetrievalUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 混合检索编排器（Phase 6 核心管线）：
 * Query → [Vector Search ‖ Keyword Search(BM25)] → RRF → Reranker → Final TopK。
 *
 * 职责：只做编排与降级，不实现任何检索算法（算法在 Provider / RRF / Reranker 里）。
 *
 * 降级矩阵（全部可观察：fallback 代码写入 outcome，且 log.warn，绝不静默）：
 *   VECTOR 失败 → KEYWORD 降级（HYBRID 模式下单路失败同理，effectiveMode 如实反映）
 *   KEYWORD 失败 → VECTOR 降级
 *   两路全败 → RETRIEVAL_UNAVAILABLE（503）
 *   Reranker 失败 → 沿用 RRF 结果（RERANKER_FALLBACK_TO_RRF）
 *   Reranker 未启用 → NOT_CONFIGURED（非错误状态，结果状态如实标注）
 *
 * 并发策略：默认串行执行两路（Mac 本地环境优先稳定；Milvus 与 BM25 均为毫秒级，
 * 并行收益小于资源风险——08-performance：没有测量数据不做并发优化）。
 */
@Service
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    /** 降级代码（日志、outcome.fallbacks 与 debug trace 共用，是可观测契约）。 */
    public static final String FALLBACK_VECTOR_TO_KEYWORD = "VECTOR_FALLBACK_TO_KEYWORD";
    public static final String FALLBACK_KEYWORD_TO_VECTOR = "KEYWORD_FALLBACK_TO_VECTOR";
    public static final String FALLBACK_RERANKER_TO_RRF = "RERANKER_FALLBACK_TO_RRF";

    /** Reranker 状态（结果可解释性契约）。 */
    public static final String RERANKER_NOT_CONFIGURED = "NOT_CONFIGURED";
    public static final String RERANKER_APPLIED = "APPLIED";
    public static final String RERANKER_SKIPPED_EMPTY = "SKIPPED_EMPTY";

    private final RetrievalProvider vectorProvider;
    private final RetrievalProvider keywordProvider;
    private final LexicalReranker reranker;
    private final AiProperties aiProperties;
    private final RetrievalProperties properties;

    public HybridRetriever(@org.springframework.beans.factory.annotation.Qualifier("vectorRetrievalProvider")
                           RetrievalProvider vectorProvider,
                           @org.springframework.beans.factory.annotation.Qualifier("keywordRetrievalProvider")
                           RetrievalProvider keywordProvider,
                           LexicalReranker reranker,
                           AiProperties aiProperties,
                           RetrievalProperties properties) {
        this.vectorProvider = vectorProvider;
        this.keywordProvider = keywordProvider;
        this.reranker = reranker;
        this.aiProperties = aiProperties;
        this.properties = properties;
    }

    /**
     * 执行一次检索。
     *
     * @param query 已校验非空的查询（校验在 RetrievalService，本类不重复）
     * @param mode  检索模式
     * @param topK  最终返回条数
     */
    public RetrievalOutcome retrieve(String query, RetrievalMode mode, int topK) {
        long startedAt = System.currentTimeMillis();
        RetrievalProperties.Candidates candidatesCfg = properties.candidatesOrDefault();

        List<RetrievalCandidate> vectorCandidates = List.of();
        List<RetrievalCandidate> keywordCandidates = List.of();
        List<String> fallbacks = new ArrayList<>();
        List<String> failureReasons = new ArrayList<>();
        long vectorMs = -1;
        long keywordMs = -1;
        RetrievalMode effectiveMode = mode;

        switch (mode) {
            case VECTOR -> {
                long t0 = System.currentTimeMillis();
                List<RetrievalCandidate> result = retrieveWithFallback(query, candidatesCfg.vector(),
                        vectorProvider, keywordProvider, candidatesCfg.keyword(), fallbacks, failureReasons);
                vectorMs = System.currentTimeMillis() - t0;
                if (fallbacks.contains(FALLBACK_VECTOR_TO_KEYWORD)) {
                    // 降级后的结果属于关键词路：归位到正确变量（fuse 按生效模式取对应列表）
                    keywordCandidates = result;
                    effectiveMode = RetrievalMode.KEYWORD;
                    keywordMs = vectorMs;
                } else {
                    vectorCandidates = result;
                }
            }
            case KEYWORD -> {
                long t0 = System.currentTimeMillis();
                List<RetrievalCandidate> result = retrieveWithFallback(query, candidatesCfg.keyword(),
                        keywordProvider, vectorProvider, candidatesCfg.vector(), fallbacks, failureReasons);
                keywordMs = System.currentTimeMillis() - t0;
                if (fallbacks.contains(FALLBACK_KEYWORD_TO_VECTOR)) {
                    vectorCandidates = result;
                    effectiveMode = RetrievalMode.VECTOR;
                    vectorMs = keywordMs;
                } else {
                    keywordCandidates = result;
                }
            }
            case HYBRID -> {
                long t0 = System.currentTimeMillis();
                try {
                    vectorCandidates = vectorProvider.retrieve(query, candidatesCfg.vector());
                } catch (com.obsidianmind.exception.VaultNotFoundException e) {
                    throw e; // 前置条件缺失：未连接 Vault，两路同样会失败，不降级（保持 404 契约）
                } catch (RuntimeException e) {
                    log.warn("向量检索失败，混合模式降级为纯关键词: {}", e.getMessage());
                    fallbacks.add(FALLBACK_VECTOR_TO_KEYWORD);
                    failureReasons.add("vector: " + e.getMessage());
                }
                vectorMs = System.currentTimeMillis() - t0;

                long t1 = System.currentTimeMillis();
                try {
                    keywordCandidates = keywordProvider.retrieve(query, candidatesCfg.keyword());
                } catch (com.obsidianmind.exception.VaultNotFoundException e) {
                    throw e;
                } catch (RuntimeException e) {
                    log.warn("关键词检索失败，混合模式降级为纯向量: {}", e.getMessage());
                    fallbacks.add(FALLBACK_KEYWORD_TO_VECTOR);
                    failureReasons.add("keyword: " + e.getMessage());
                }
                keywordMs = System.currentTimeMillis() - t1;

                if (fallbacks.contains(FALLBACK_VECTOR_TO_KEYWORD)
                        && fallbacks.contains(FALLBACK_KEYWORD_TO_VECTOR)) {
                    throw new RetrievalUnavailableException("向量检索与关键词检索均不可用");
                }
                effectiveMode = fallbacks.contains(FALLBACK_VECTOR_TO_KEYWORD) ? RetrievalMode.KEYWORD
                        : (fallbacks.contains(FALLBACK_KEYWORD_TO_VECTOR) ? RetrievalMode.VECTOR : mode);
            }
        }

        // ── 融合与重排 ────────────────────────────────────────────────
        long fusionStartedAt = System.currentTimeMillis();
        List<RetrievalCandidate> fused = fuse(vectorCandidates, keywordCandidates, effectiveMode);
        long fusionMs = System.currentTimeMillis() - fusionStartedAt;

        long rerankerStartedAt = System.currentTimeMillis();
        RerankResult rerankResult = rerank(query, fused, topK, fallbacks);
        long rerankerMs = System.currentTimeMillis() - rerankerStartedAt;

        long totalMs = System.currentTimeMillis() - startedAt;
        log.info("混合检索: mode={} effective={} vector={} keyword={} fused={} final={} "
                        + "vectorMs={} keywordMs={} fusionMs={} rerankerMs={} totalMs={} fallbacks={}",
                mode, effectiveMode, vectorCandidates.size(), keywordCandidates.size(),
                fused.size(), rerankResult.finalCandidates().size(),
                vectorMs, keywordMs, fusionMs, rerankerMs, fallbacks);

        return new RetrievalOutcome(mode, effectiveMode, List.copyOf(fallbacks), List.copyOf(failureReasons),
                rerankResult.status(), vectorCandidates, keywordCandidates, fused,
                rerankResult.finalCandidates(), vectorMs, keywordMs, fusionMs, rerankerMs, totalMs);
    }

    /** 单模式检索：主路失败时按矩阵降级到备路（fallback 代码追加）。 */
    private List<RetrievalCandidate> retrieveWithFallback(String query, int primaryCount,
                                                          RetrievalProvider primary,
                                                          RetrievalProvider fallback,
                                                          int fallbackCount,
                                                          List<String> fallbacks,
                                                          List<String> failureReasons) {
        try {
            return primary.retrieve(query, primaryCount);
        } catch (com.obsidianmind.exception.VaultNotFoundException e) {
            throw e; // 未连接 Vault：前置条件失败不降级（保持 404 契约）
        } catch (RuntimeException e) {
            String code = primary == vectorProvider
                    ? FALLBACK_VECTOR_TO_KEYWORD : FALLBACK_KEYWORD_TO_VECTOR;
            log.warn("检索失败，降级: primary={} code={} reason={}", primary.name(), code, e.getMessage());
            fallbacks.add(code);
            failureReasons.add(primary.name() + ": " + e.getMessage());
            return fallback.retrieve(query, fallbackCount);
        }
    }

    /** 按生效模式融合：HYBRID 走 RRF；单模式直接沿用该路 ranked list。 */
    private List<RetrievalCandidate> fuse(List<RetrievalCandidate> vectorCandidates,
                                          List<RetrievalCandidate> keywordCandidates,
                                          RetrievalMode effectiveMode) {
        if (effectiveMode == RetrievalMode.HYBRID) {
            if (vectorCandidates.isEmpty() && keywordCandidates.isEmpty()) {
                return List.of();
            }
            if (vectorCandidates.isEmpty()) {
                return keywordCandidates;
            }
            if (keywordCandidates.isEmpty()) {
                return vectorCandidates;
            }
            return ReciprocalRankFusion.fuse(List.of(vectorCandidates, keywordCandidates),
                    properties.rrfOrDefault().k());
        }
        if (effectiveMode == RetrievalMode.KEYWORD) {
            return keywordCandidates;
        }
        return vectorCandidates;
    }

    /** Reranker 阶段：TopN 重排 + 尾部拼接。失败降级 RRF（可观察）。 */
    private RerankResult rerank(String query, List<RetrievalCandidate> fused, int topK,
                                List<String> fallbacks) {
        if (fused.isEmpty()) {
            return new RerankResult(List.of(), RERANKER_SKIPPED_EMPTY);
        }
        if (!reranker.isAvailable()) {
            // NOT_CONFIGURED：finalScore 语义 = rrfScore（候选分数通道见 RetrievalCandidate 注释）
            return new RerankResult(fused.stream().limit(topK).toList(), RERANKER_NOT_CONFIGURED);
        }
        int rerankTopN = Math.min(Math.max(1, properties.rerankerOrDefault().topN()), fused.size());
        try {
            List<RetrievalCandidate> head = reranker.rerank(query, fused.subList(0, rerankTopN));
            List<RetrievalCandidate> tail = fused.subList(rerankTopN, fused.size());
            List<RetrievalCandidate> combined = new ArrayList<>(head.size() + tail.size());
            combined.addAll(head);
            combined.addAll(tail);
            return new RerankResult(combined.stream().limit(topK).toList(), RERANKER_APPLIED);
        } catch (RuntimeException e) {
            log.warn("Reranker 失败，沿用 RRF 结果: {}", e.getMessage());
            fallbacks.add(FALLBACK_RERANKER_TO_RRF);
            return new RerankResult(fused.stream().limit(topK).toList(), RERANKER_APPLIED);
        }
    }

    private record RerankResult(List<RetrievalCandidate> finalCandidates, String status) {
    }

    /**
     * 检索结果 + 全链路 trace（Source 产出与 debug 面板共用同一数据，不造假两套）。
     */
    public record RetrievalOutcome(
            RetrievalMode requestedMode,
            RetrievalMode effectiveMode,
            List<String> fallbacks,
            List<String> failureReasons,
            String rerankerStatus,
            List<RetrievalCandidate> vectorCandidates,
            List<RetrievalCandidate> keywordCandidates,
            List<RetrievalCandidate> fusedCandidates,
            List<RetrievalCandidate> finalCandidates,
            long vectorMs,
            long keywordMs,
            long fusionMs,
            long rerankerMs,
            long totalMs) {
    }
}
