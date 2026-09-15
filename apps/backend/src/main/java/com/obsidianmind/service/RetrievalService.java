package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.exception.EmbeddingDimensionMismatchException;
import com.obsidianmind.exception.EmbeddingException;
import com.obsidianmind.exception.InvalidRequestException;
import com.obsidianmind.repository.VectorRepository;
import com.obsidianmind.repository.VectorRepository.SearchResultRecord;
import com.obsidianmind.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 语义检索服务（Phase 4）：Query → Embedding → Vector Search → Filter/Dedup → Sources。
 *
 * 只做检索编排，不触碰 Milvus SDK；不生成 LLM Answer（Retrieval 与 Answer 解耦，
 * 检索质量由评估集确定性回归，见 docs/architecture/retrieval-pipeline.md）。
 *
 * 设计要点：
 * - 超采样检索（topK × 3，≤ max-top-k）：为阈值过滤与同文档多样性裁剪留余量，最终仍裁回 topK；
 * - Source 全部来自向量元数据，无 N+1 补查；路径保持 Vault-relative，绝不泄露本机绝对路径；
 * - "无匹配"返回空列表（200），不是 500——没有知识匹配不是服务器错误。
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final VaultRepository vaultRepository;
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;
    private final MilvusProperties milvusProperties;
    private final AiProperties aiProperties;

    public RetrievalService(VaultRepository vaultRepository,
                            EmbeddingService embeddingService,
                            VectorRepository vectorRepository,
                            MilvusProperties milvusProperties,
                            AiProperties aiProperties) {
        this.vaultRepository = vaultRepository;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.milvusProperties = milvusProperties;
        this.aiProperties = aiProperties;
    }

    /**
     * 语义检索（面向 UI：Source 摘要为 snippet 截断版）。
     *
     * @param rawQuery      用户查询（空白拒绝；长度受 ai.retrieval.max-query-length 限制）
     * @param requestedTopK 客户端期望条数（null 用默认；超上限夹取，不报错）
     * @return 排序后的 Source 列表；无匹配时 results 为空
     * @throws InvalidRequestException                 query 非法（400）
     * @throws com.obsidianmind.exception.OllamaUnavailableException Query 向量化失败（503）
     * @throws EmbeddingDimensionMismatchException     向量维度与 Collection 配置矛盾（500）
     */
    public RetrievalResult retrieve(String rawQuery, Integer requestedTopK) {
        CoreResult core = doRetrieve(rawQuery, requestedTopK);
        AiProperties.Retrieval cfg = aiProperties.retrievalOrDefault();
        List<Source> sources = core.chunks().stream()
                .map(c -> new Source(c.title(), c.path(), c.heading(),
                        snippet(c.content(), cfg.snippetLength()), c.score(), c.documentId(), c.chunkIndex()))
                .toList();
        return new RetrievalResult(core.query(), List.copyOf(sources), core.elapsedMs());
    }

    /**
     * 语义检索（面向 RAG Context：保留完整 Chunk 内容，不做 snippet 截断）。
     * 与 retrieve() 共用同一检索核（校验/嵌入/搜索/阈值/去重/多样性裁剪），只是产出层不同：
     * Context 需要完整内容供 LLM 阅读，UI Source 只需要摘要。
     */
    public RagRetrievalResult retrieveForRag(String rawQuery, Integer requestedTopK) {
        CoreResult core = doRetrieve(rawQuery, requestedTopK);
        List<RagSource> sources = core.chunks().stream()
                .map(c -> new RagSource(c.title(), c.path(), c.heading(), c.content(),
                        c.score(), c.documentId(), c.chunkIndex()))
                .toList();
        return new RagRetrievalResult(core.query(), List.copyOf(sources), core.elapsedMs());
    }

    /** 检索核：Validation → vaultId 边界 → Query Embedding → Vector Search → Filter → Dedup → Rank。 */
    private CoreResult doRetrieve(String rawQuery, Integer requestedTopK) {
        long startedAt = System.currentTimeMillis();
        AiProperties.Retrieval cfg = aiProperties.retrievalOrDefault();

        // ① Validation：空白拒绝、长度上限（防恶意超长查询）、topK 夹取（防超大 TopK 打爆内存）
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.isEmpty()) {
            throw new InvalidRequestException("query 不能为空");
        }
        if (query.length() > cfg.maxQueryLength()) {
            throw new InvalidRequestException("query 过长（上限 " + cfg.maxQueryLength() + " 字符）");
        }
        int topK = clampTopK(requestedTopK, cfg);

        // ② 检索边界：只查当前已连接 Vault（vaultId 为服务端计算的根路径哈希，非用户输入）
        String vaultId = vaultRepository.requireVaultId();

        // ③ Query Embedding：与索引阶段同一 EmbeddingService / 同一模型，保证同一向量空间
        long embeddingStartedAt = System.currentTimeMillis();
        List<float[]> vectors = embeddingService.embed(List.of(query));
        long embeddingMs = System.currentTimeMillis() - embeddingStartedAt;
        if (vectors.isEmpty() || vectors.get(0).length == 0) {
            throw new EmbeddingException("Query 向量化结果为空");
        }
        float[] queryVector = vectors.get(0);
        if (queryVector.length != milvusProperties.vectorDimension()) {
            throw new EmbeddingDimensionMismatchException(
                    "Query 向量维度 " + queryVector.length + " 与 Collection 配置 "
                            + milvusProperties.vectorDimension() + " 不符（换模型后需同步 milvus.vector-dimension 并重建索引）");
        }

        // ④ Vector Search：vaultId 过滤在查询内完成；超采样为去重/阈值损失留余量
        int fetch = Math.min(cfg.maxTopK(), Math.max(topK * 3, topK));
        long searchStartedAt = System.currentTimeMillis();
        List<SearchResultRecord> candidates =
                vectorRepository.search(milvusProperties.collection(), vaultId, queryVector, fetch);
        long searchMs = System.currentTimeMillis() - searchStartedAt;

        // ⑤ Filter → Dedup → Rank：candidates 已按 score 降序，此处做阈值、精确重复剔除与同文档多样性裁剪
        List<ScoredChunk> chunks = scoreAndDedup(candidates, cfg, topK);
        long totalMs = System.currentTimeMillis() - startedAt;

        log.info("语义检索: queryLen={} topK={} candidates={} results={} embeddingMs={} searchMs={} totalMs={}",
                query.length(), topK, candidates.size(), chunks.size(), embeddingMs, searchMs, totalMs);
        return new CoreResult(query, chunks, totalMs);
    }

    /** 阈值过滤（默认关闭）→ 同文档多样性上限 → 精确重复内容剔除 → 裁回 topK。保留完整内容，截断由产出层决定。 */
    private List<ScoredChunk> scoreAndDedup(List<SearchResultRecord> candidates, AiProperties.Retrieval cfg, int topK) {
        Map<String, Integer> perDocument = new LinkedHashMap<>();
        Set<String> seenContent = new HashSet<>();
        List<ScoredChunk> chunks = new java.util.ArrayList<>(topK);
        for (SearchResultRecord record : candidates) {
            if (chunks.size() >= topK) {
                break; // 已按 score 降序：取满即止，无需继续
            }
            if (cfg.scoreThreshold() > 0 && record.score() < cfg.scoreThreshold()) {
                break; // 候选有序，后继只会更低
            }
            if (record.content() != null && !seenContent.add(record.content())) {
                continue; // overlap 产生的精确重复 Chunk：对用户是噪声
            }
            int count = perDocument.merge(record.documentId(), 1, Integer::sum);
            if (count > cfg.maxPerDocument()) {
                continue; // 文档级多样性：一篇笔记最多保留 max-per-document 条
            }
            chunks.add(new ScoredChunk(
                    record.title(),
                    record.relativePath(),
                    record.headingPath(),
                    record.content() == null ? "" : record.content(),
                    record.score(),
                    record.documentId(),
                    record.chunkIndex()));
        }
        return List.copyOf(chunks);
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

    /** 检索核的内部统一形状：content 为完整 Chunk 内容（snippet / RAG Context 由产出层各自派生）。 */
    private record ScoredChunk(
            String title,
            String path,
            String heading,
            String content,
            double score,
            String documentId,
            int chunkIndex) {
    }

    private record CoreResult(String query, List<ScoredChunk> chunks, long elapsedMs) {
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
