package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.RetrievalProperties;
import com.obsidianmind.exception.InvalidRequestException;
import com.obsidianmind.retrieval.HybridRetriever;
import com.obsidianmind.retrieval.RetrievalMode;
import com.obsidianmind.retrieval.RetrievalCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检索服务（Phase 4 建立，Phase 6 升级为混合检索门面）：
 * Query → 校验 → HybridRetriever（Vector ‖ Keyword → RRF → Reranker）→ 产出层规则 → Sources。
 *
 * 分层职责：
 * - 本类：校验（空白/长度/topK 夹取）+ 产出层规则（精确重复剔除、同文档多样性、VECTOR 模式相似度阈值）
 *   + Source 形状映射（snippet 截断）。不知道 Milvus / BM25 / RRF 的存在；
 * - HybridRetriever：模式编排、两路召回、RRF、Reranker、降级（fallback 全部可观察）；
 * - RAG 链路（RagAnswerService）只调 retrieveForRag，不感知模式——解耦保持不变。
 *
 * 产出层规则语义（Phase 6 明确化）：
 * - 精确重复剔除 / 同文档多样性：对所有模式生效（用户可见噪声治理）；
 * - scoreThreshold（COSINE 语义）：仅 effectiveMode == VECTOR 时生效——BM25 分无上界、
 *   RRF 分量级 ~1/(k+rank)，同一阈值跨分数体系没有意义；
 * - Source.score = 候选 finalScore（rerankScore > rrfScore > 唯一路原始分，见 RetrievalCandidate）。
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final HybridRetriever hybridRetriever;
    private final AiProperties aiProperties;
    private final RetrievalProperties retrievalProperties;

    public RetrievalService(HybridRetriever hybridRetriever,
                            AiProperties aiProperties,
                            RetrievalProperties retrievalProperties) {
        this.hybridRetriever = hybridRetriever;
        this.aiProperties = aiProperties;
        this.retrievalProperties = retrievalProperties;
    }

    /** 默认检索模式（retrieval.default-mode，缺省 HYBRID）。 */
    public RetrievalMode defaultMode() {
        return RetrievalMode.parse(null, retrievalProperties.defaultModeOrDefault());
    }

    /**
     * 混合检索（面向 UI：Source 摘要为 snippet 截断版）。
     *
     * @param rawQuery      用户查询（空白拒绝；长度受 ai.retrieval.max-query-length 限制）
     * @param mode          检索模式（null = 配置默认）
     * @param requestedTopK 客户端期望条数（null 用默认；超上限夹取，不报错）
     * @throws InvalidRequestException query 非法（400）
     * @throws com.obsidianmind.exception.RetrievalUnavailableException 两路全败（503）
     */
    public HybridRetrievalResult retrieveHybrid(String rawQuery, RetrievalMode mode, Integer requestedTopK) {
        AiProperties.Retrieval cfg = aiProperties.retrievalOrDefault();
        String query = validate(rawQuery, cfg);
        int topK = clampTopK(requestedTopK, cfg);
        RetrievalMode resolvedMode = mode != null ? mode : defaultMode();

        HybridRetriever.RetrievalOutcome outcome = hybridRetriever.retrieve(query, resolvedMode, topK);
        List<Source> sources = postProcess(outcome, cfg, topK).stream()
                .map(c -> new Source(c.title(), c.path(), c.heading(),
                        snippet(c.content(), cfg.snippetLength()), c.finalScore(),
                        c.documentId(), c.chunkIndex()))
                .toList();
        return new HybridRetrievalResult(query, outcome.requestedMode(), outcome.effectiveMode(),
                outcome.fallbacks(), outcome.rerankerStatus(), List.copyOf(sources), outcome.totalMs());
    }

    /** 语义/混合检索（面向 UI，配置默认模式）：兼容 Phase 4 语义检索入口。 */
    public RetrievalResult retrieve(String rawQuery, Integer requestedTopK) {
        HybridRetrievalResult hybrid = retrieveHybrid(rawQuery, null, requestedTopK);
        return new RetrievalResult(hybrid.query(), hybrid.results(), hybrid.elapsedMs());
    }

    /**
     * 混合检索（面向 RAG Context：保留完整 Chunk 内容，不做 snippet 截断）。
     * RAG 不感知模式：使用配置默认模式（retrieval.default-mode）。
     */
    public RagRetrievalResult retrieveForRag(String rawQuery, Integer requestedTopK) {
        AiProperties.Retrieval cfg = aiProperties.retrievalOrDefault();
        String query = validate(rawQuery, cfg);
        int topK = clampTopK(requestedTopK, cfg);
        HybridRetriever.RetrievalOutcome outcome = hybridRetriever.retrieve(query, defaultMode(), topK);
        List<RagSource> sources = postProcess(outcome, cfg, topK).stream()
                .map(c -> new RagSource(c.title(), c.path(), c.heading(), c.content(),
                        c.finalScore(), c.documentId(), c.chunkIndex()))
                .toList();
        // RAG 诚实原则：发生过降级且仍无结果 → 抛检索不可用（503），绝不把"外部依赖挂了"
        // 伪装成"知识库没有相关内容"——后者会让用户相信错误答案，前者引导用户检查服务。
        if (sources.isEmpty() && !outcome.fallbacks().isEmpty()) {
            throw new com.obsidianmind.exception.RetrievalUnavailableException(
                    "检索降级后仍无结果: " + String.join("; ", outcome.failureReasons()));
        }
        return new RagRetrievalResult(query, List.copyOf(sources), outcome.totalMs());
    }

    /** Debug 全链路 trace（仅 retrieval.debug-enabled 开启时由 Controller 暴露）。 */
    public HybridRetriever.RetrievalOutcome retrieveWithTrace(String rawQuery, RetrievalMode mode,
                                                              Integer requestedTopK) {
        AiProperties.Retrieval cfg = aiProperties.retrievalOrDefault();
        String query = validate(rawQuery, cfg);
        int topK = clampTopK(requestedTopK, cfg);
        return hybridRetriever.retrieve(query, mode != null ? mode : defaultMode(), topK);
    }

    /** 产出层规则：精确重复剔除 → 同文档多样性 → VECTOR 模式阈值过滤 → 裁回 topK。 */
    private List<RetrievalCandidate> postProcess(HybridRetriever.RetrievalOutcome outcome,
                                                 AiProperties.Retrieval cfg, int topK) {
        Map<String, Integer> perDocument = new LinkedHashMap<>();
        Set<String> seenContent = new HashSet<>();
        List<RetrievalCandidate> chunks = new java.util.ArrayList<>(topK);
        for (RetrievalCandidate candidate : outcome.finalCandidates()) {
            if (chunks.size() >= topK) {
                break; // finalCandidates 已按 finalScore 降序：取满即止
            }
            if (outcome.effectiveMode() == RetrievalMode.VECTOR
                    && cfg.scoreThreshold() > 0 && candidate.vectorScore() < cfg.scoreThreshold()) {
                break; // 阈值只对 COSINE 分有语义；候选有序，后继只会更低
            }
            if (candidate.content() != null && !seenContent.add(candidate.content())) {
                continue; // overlap 产生的精确重复 Chunk：对用户是噪声
            }
            int count = perDocument.merge(candidate.documentId(), 1, Integer::sum);
            if (count > cfg.maxPerDocument()) {
                continue; // 文档级多样性：一篇笔记最多保留 max-per-document 条
            }
            chunks.add(candidate);
        }
        return List.copyOf(chunks);
    }

    private String validate(String rawQuery, AiProperties.Retrieval cfg) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.isEmpty()) {
            throw new InvalidRequestException("query 不能为空");
        }
        if (query.length() > cfg.maxQueryLength()) {
            throw new InvalidRequestException("query 过长（上限 " + cfg.maxQueryLength() + " 字符）");
        }
        return query;
    }

    private static int clampTopK(Integer requested, AiProperties.Retrieval cfg) {
        if (requested == null) {
            return cfg.defaultTopK();
        }
        return Math.max(1, Math.min(requested, cfg.maxTopK()));
    }

    /**
     * 折叠空白并做码点安全截断：按 codePoint 前进而非 char 下标，避免把 surrogate pair（emoji、生僻字）拦腰截断。
     * 去掉行首 Markdown 标题符，摘要对用户可读。
     */
    private static String snippet(String content, int maxLength) {
        String flat = content == null ? "" : content.strip().replaceAll("\\s+", " ");
        flat = flat.replaceFirst("^#{1,6}\\s+", "");
        if (maxLength <= 0 || flat.codePointCount(0, flat.length()) <= maxLength) {
            return flat;
        }
        int end = flat.offsetByCodePoints(0, maxLength);
        return flat.substring(0, end) + "…";
    }

    /** 检索结果（面向用户的 Source 形状；title/path/heading/snippet/score 供 UI，documentId/chunkIndex 供内部定位）。 */
    public record Source(
            String title,
            String path,
            String heading,
            String snippet,
            double score,
            String documentId,
            int chunkIndex) {
    }

    public record RetrievalResult(String query, List<Source> results, long elapsedMs) {
    }

    /** 混合检索结果：Source 形状与 Phase 4 完全兼容 + 模式可观察字段（requested/effective/fallbacks/reranker）。 */
    public record HybridRetrievalResult(
            String query,
            RetrievalMode requestedMode,
            RetrievalMode effectiveMode,
            List<String> fallbacks,
            String rerankerStatus,
            List<Source> results,
            long elapsedMs) {
    }

    /** RAG 检索结果（面向 ContextAssembler；content 为完整 Chunk 内容，禁止直接暴露给 UI）。 */
    public record RagSource(
            String title,
            String path,
            String heading,
            String content,
            double score,
            String documentId,
            int chunkIndex) {
    }

    public record RagRetrievalResult(String query, List<RagSource> results, long elapsedMs) {
    }
}
