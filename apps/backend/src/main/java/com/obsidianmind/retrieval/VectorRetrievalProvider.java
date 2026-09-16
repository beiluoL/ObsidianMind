package com.obsidianmind.retrieval;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.exception.EmbeddingDimensionMismatchException;
import com.obsidianmind.exception.EmbeddingException;
import com.obsidianmind.repository.VectorRepository;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.service.EmbeddingService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量检索 Provider（Phase 6）：包装既有 语义检索核（Query → Embedding → Milvus search）。
 *
 * 职责边界：只做"取回 ranked 候选"，不做阈值过滤 / 去重 / 多样性裁剪——
 * 那些是 RRF 融合后由 RetrievalService 统一施加的产出层规则（融合阶段必须看到原始 ranked list）。
 * 异常语义不变：Ollama 不可用 → OLLAMA_UNAVAILABLE（503），Milvus 不可用 → MILVUS_UNAVAILABLE（503）。
 */
@Component
public class VectorRetrievalProvider implements RetrievalProvider {

    private final VaultRepository vaultRepository;
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;
    private final MilvusProperties milvusProperties;
    private final AiProperties aiProperties;

    public VectorRetrievalProvider(VaultRepository vaultRepository,
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

    @Override
    public String name() {
        return "vector";
    }

    @Override
    public boolean isAvailable() {
        // 廉价检查：Vault 已连接即可尝试；Milvus/Ollama 真实可用性只能在 retrieve 时暴露
        return vaultRepository.isConnected();
    }

    @Override
    public List<RetrievalCandidate> retrieve(String query, int candidateCount) {
        String vaultId = vaultRepository.requireVaultId();

        // Query Embedding：与索引阶段同一 EmbeddingService / 同一模型（项目硬约束）
        List<float[]> vectors = embeddingService.embed(List.of(query));
        if (vectors.isEmpty() || vectors.get(0).length == 0) {
            throw new EmbeddingException("Query 向量化结果为空");
        }
        float[] queryVector = vectors.get(0);
        if (queryVector.length != milvusProperties.vectorDimension()) {
            throw new EmbeddingDimensionMismatchException(
                    "Query 向量维度 " + queryVector.length + " 与 Collection 配置 "
                            + milvusProperties.vectorDimension() + " 不符（换模型后需同步 milvus.vector-dimension 并重建索引）");
        }

        int fetch = Math.max(1, candidateCount);
        List<VectorRepository.SearchResultRecord> records =
                vectorRepository.search(milvusProperties.collection(), vaultId, queryVector, fetch);
        return records.stream()
                .map(r -> new RetrievalCandidate(
                        r.chunkId(), r.documentId(), r.relativePath(), r.title(), r.headingPath(),
                        r.chunkIndex(), r.content() == null ? "" : r.content(),
                        r.score(), Double.NaN, Double.NaN, Double.NaN,
                        RetrievalCandidate.RetrievalType.VECTOR))
                .toList();
    }
}
