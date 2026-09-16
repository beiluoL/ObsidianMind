package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.domain.Chunk;
import com.obsidianmind.exception.InvalidRequestException;
import com.obsidianmind.exception.OllamaUnavailableException;
import com.obsidianmind.parser.FrontmatterParser;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.WikiLinkParser;
import com.obsidianmind.retrieval.HybridRetriever;
import com.obsidianmind.retrieval.RetrievalStackForTest;
import com.obsidianmind.repository.InMemoryVectorStore;
import com.obsidianmind.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RetrievalService 行为测试：校验、检索、空结果、阈值、Vault 隔离、去重多样性、Source 映射与 snippet。
 * Embedding 用确定性桩（文本关键词 → 单位向量），向量库用内存实现，Vault 用 @TempDir。
 * 默认模式设为 VECTOR：本类验证向量语义（阈值/隔离/多样性）；混合模式行为见 HybridRetrieverTest。
 */
class RetrievalServiceTest {

    private static final String COLLECTION = "c";
    private static final int DIM = 4;

    @TempDir
    Path vaultDir;

    private final InMemoryVectorStore vectorStore = new InMemoryVectorStore();
    private final VaultRepository vaultRepository = new VaultRepository();
    private AiProperties ai;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(vaultDir);
        vaultRepository.connect(vaultDir.toString());
        vectorStore.ensureCollection(COLLECTION, DIM);
        ai = new AiProperties("ollama", null, null, null, cfg(5, 20, 0, 2, 200), null);
    }

    private RetrievalService service() {
        return service(stubEmbedding());
    }

    private RetrievalService service(EmbeddingService embedding) {
        return newService(embedding, vectorStore);
    }

    /** 按生产相同方式装配混合检索栈（默认 VECTOR，保持本类向量语义断言不变）。 */
    private RetrievalService newService(EmbeddingService embedding, InMemoryVectorStore store) {
        MarkdownParser parser = new MarkdownParser(new FrontmatterParser(), new WikiLinkParser());
        HybridRetriever hybrid = RetrievalStackForTest.hybridRetriever(vaultRepository, embedding, store,
                new MilvusProperties("localhost", 19530, 2, COLLECTION, DIM), ai, parser,
                new Chunker(ai), RetrievalStackForTest.properties("VECTOR"));
        return RetrievalStackForTest.retrievalService(hybrid, ai, RetrievalStackForTest.properties("VECTOR"));
    }

    private AiProperties.Retrieval cfg(int topK, int maxTopK, double threshold, int maxPerDoc, int snippetLen) {
        return new AiProperties.Retrieval(topK, maxTopK, threshold, maxPerDoc, snippetLen, 512);
    }

    /** 确定性 Embedding 桩：含 alpha → [1,0,0,0]；含 beta → [0,1,0,0]；否则全零。 */
    private static EmbeddingService stubEmbedding() {
        return new EmbeddingService() {
            @Override
            public String provider() {
                return "stub";
            }

            @Override
            public String modelName() {
                return "test-embed";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public List<float[]> embed(List<String> texts) {
                return texts.stream().<float[]>map(t -> {
                    if (t.contains("alpha")) {
                        return new float[]{1, 0, 0, 0};
                    }
                    if (t.contains("beta")) {
                        return new float[]{0, 1, 0, 0};
                    }
                    return new float[]{0, 0, 0, 0};
                }).toList();
            }
        };
    }

    private void index(String documentId, String heading, String content) {
        index(documentId, heading, content, vaultRepository.requireVaultId());
    }

    private void index(String documentId, String heading, String content, String vaultId) {
        Chunk chunk = new Chunk(documentId + "#c0", vaultId, documentId, documentId, documentId, heading, 0,
                0, content.length(), content, "hash-" + documentId);
        vectorStore.upsert(COLLECTION, List.of(chunk), List.of(
                content.contains("alpha") ? new float[]{1, 0, 0, 0} : new float[]{0, 1, 0, 0}));
    }

    @Test
    void blankQueryIsRejected() {
        assertThatThrownBy(() -> service().retrieve("   ", null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service().retrieve(null, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void oversizedQueryIsRejected() {
        ai = new AiProperties("ollama", null, null, null, cfg(5, 20, 0, 2, 200), null);
        assertThatThrownBy(() -> service().retrieve("很".repeat(513), null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("过长");
        // 恰好 512 字符：允许
        assertThat(service().retrieve("很".repeat(512), null).results()).isEmpty();
    }

    @Test
    void retrievesMatchingSourceWithMetadata() {
        ai = new AiProperties("ollama", null, null, null, cfg(5, 20, 0, 2, 200), null);
        index("Java/HashMap.md", "HashMap / 扩容机制", "alpha 扩容机制的触发条件与过程。");
        index("AI/Token.md", "Token", "beta 其他主题内容。");

        RetrievalService.RetrievalResult result = service().retrieve("alpha 如何扩容", null);
        // 阈值默认关闭：0 分候选也会返回（诚实行为），断言排序与首位正确性
        assertThat(result.results()).isNotEmpty();
        assertThat(result.results().get(0).title()).isEqualTo("Java/HashMap.md");
        RetrievalService.Source source = result.results().get(0);
        assertThat(source.title()).isEqualTo("Java/HashMap.md");
        assertThat(source.path()).isEqualTo("Java/HashMap.md"); // Vault-relative，不含绝对路径
        assertThat(source.heading()).isEqualTo("HashMap / 扩容机制");
        assertThat(source.snippet()).contains("alpha 扩容机制");
        assertThat(source.score()).isGreaterThan(0.99);
        assertThat(source.documentId()).isEqualTo("Java/HashMap.md");
        assertThat(source.chunkIndex()).isZero();
        assertThat(result.elapsedMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void emptyResultReturnsEmptyListNotError() {
        ai = new AiProperties("ollama", null, null, null, cfg(5, 20, 0, 2, 200), null);
        // 索引存在但查询方向无任何接近向量（全零 vs 单位向量）仍会返回 0 分结果，
        // 这里验证的是"索引里根本没有该 Vault 数据"→ 空列表，不抛异常
        RetrievalService empty = newRetrievalServiceWithEmptyStore();
        assertThat(empty.retrieve("alpha 查询", null).results()).isEmpty();
    }

    private RetrievalService newRetrievalServiceWithEmptyStore() {
        InMemoryVectorStore emptyStore = new InMemoryVectorStore();
        emptyStore.ensureCollection(COLLECTION, DIM);
        return newService(stubEmbedding(), emptyStore);
    }

    @Test
    void scoreThresholdFiltersWeakMatches() {
        ai = new AiProperties("ollama", null, null, null, cfg(5, 20, 0.99, 2, 200), null);
        index("Java/HashMap.md", "HashMap", "alpha 强相关。");
        // 全零内容向量与查询 cosine = 0 < 0.99，被阈值过滤
        vectorStore.upsert(COLLECTION, List.of(new Chunk("Weak.md#c0", "vault-1", "Weak.md", "Weak.md", "Weak.md", "W", 0,
                        0, 1, "其他", "h-w")),
                List.of(new float[]{0, 0, 1, 0}));

        RetrievalService.RetrievalResult result = service().retrieve("alpha 查询", null);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).path()).isEqualTo("Java/HashMap.md");
    }

    @Test
    void vaultIsolationHidesOtherVaultVectors() {
        ai = new AiProperties("ollama", null, null, null, cfg(5, 20, 0, 2, 200), null);
        index("Java/HashMap.md", "HashMap", "alpha 当前 Vault。");
        // 直接写入另一个 vaultId 的同名路径向量（模拟切换 Vault 后未清理的旧索引）
        index("Java/OtherVault.md", "Old", "alpha 旧 Vault 内容。", "vault-old");

        RetrievalService.RetrievalResult result = service().retrieve("alpha 查询", null);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).documentId()).isEqualTo("Java/HashMap.md");
    }

    @Test
    void sameDocumentIsCappedByMaxPerDocument() {
        ai = new AiProperties("ollama", null, null, null, cfg(5, 20, 0, 2, 200), null);
        // 一次 upsert 写入同文档 5 个 Chunk（upsert 是文档级 replace，分次调用会只剩最后一批）
        List<Chunk> longChunks = new java.util.ArrayList<>();
        List<float[]> longVectors = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            longChunks.add(new Chunk("Java/Long.md#c" + i, vaultRepository.requireVaultId(), "Java/Long.md", "Java/Long.md", "Long.md",
                    "L / S" + i, i, 0, 8, "alpha 第" + i + "段内容", "h"));
            longVectors.add(new float[]{1, 0, 0, 0});
        }
        vectorStore.upsert(COLLECTION, longChunks, longVectors);
        index("Java/Other.md", "O", "alpha 另一篇。");

        RetrievalService.RetrievalResult result = service().retrieve("alpha 查询", null);
        long longDocCount = result.results().stream()
                .filter(s -> s.documentId().equals("Java/Long.md")).count();
        assertThat(longDocCount).isEqualTo(2); // 多样性上限
        assertThat(result.results().get(0).score()).as("按 score 降序").isGreaterThanOrEqualTo(
                result.results().get(result.results().size() - 1).score());
    }

    @Test
    void exactDuplicateContentIsDeduplicated() {
        ai = new AiProperties("ollama", null, null, null, cfg(5, 20, 0, 5, 200), null);
        index("Java/A.md", "A", "alpha 重复内容。");
        index("Java/B.md", "B", "alpha 重复内容。"); // 与 A 的 Chunk 内容完全相同（overlap 场景）

        RetrievalService.RetrievalResult result = service().retrieve("alpha 查询", null);
        assertThat(result.results()).hasSize(1); // 精确重复只保留一条
    }

    @Test
    void snippetTruncatesOnCodePointBoundary() {
        ai = new AiProperties("ollama", null, null, null, cfg(5, 20, 0, 2, 10), null);
        String emoji = "alpha 目标完成 🎉🚀 后续内容应该被截断掉";
        index("Java/A.md", "A", emoji);

        RetrievalService.Source source = service().retrieve("alpha 查询", null).results().get(0);
        assertThat(source.snippet()).startsWith("alpha");
        assertThat(source.snippet()).endsWith("…");
        // 码点数 ≤ 10 + 省略号，且不会出现半个 surrogate（endsWith 完整字符）
        assertThat(source.snippet().codePointCount(0, source.snippet().length())).isLessThanOrEqualTo(11);
    }

    @Test
    void topKIsClampedToConfiguredBounds() {
        ai = new AiProperties("ollama", null, null, null, cfg(2, 3, 0, 5, 200), null);
        for (int i = 0; i < 6; i++) {
            index("Java/D" + i + ".md", "D" + i, "alpha 内容" + i);
        }
        // 请求 100 → 夹取到 maxTopK=3；请求 0/负数 → 至少 1
        assertThat(service().retrieve("alpha 查询", 100).results()).hasSize(3);
        assertThat(service().retrieve("alpha 查询", 0).results()).hasSize(1);
        assertThat(service().retrieve("alpha 查询", null).results()).hasSize(2);
    }

    @Test
    void embeddingFailureDegradesToKeywordAndIsObservable() {
        ai = new AiProperties("ollama", null, null, null, cfg(5, 20, 0, 2, 200), null);
        EmbeddingService failing = new EmbeddingService() {
            @Override
            public String provider() {
                return "stub";
            }

            @Override
            public String modelName() {
                return "test-embed";
            }

            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public List<float[]> embed(List<String> texts) {
                throw new OllamaUnavailableException("Ollama down");
            }
        };
        // Phase 6 语义：向量失败 → 降级关键词（可观察），空 Vault 无关键词结果 → 空列表
        RetrievalService.HybridRetrievalResult result =
                service(failing).retrieveHybrid("alpha 查询", null, null);
        assertThat(result.results()).isEmpty();
        assertThat(result.effectiveMode()).isEqualTo(com.obsidianmind.retrieval.RetrievalMode.KEYWORD);
        assertThat(result.fallbacks())
                .containsExactly(com.obsidianmind.retrieval.HybridRetriever.FALLBACK_VECTOR_TO_KEYWORD);
    }

    @Test
    void dimensionMismatchDegradesWithObservableFallback() {
        ai = new AiProperties("ollama", null, null, null, cfg(5, 20, 0, 2, 200), null);
        EmbeddingService wrongDim = new EmbeddingService() {
            @Override
            public String provider() {
                return "stub";
            }

            @Override
            public String modelName() {
                return "test-embed";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public List<float[]> embed(List<String> texts) {
                return texts.stream().map(t -> new float[]{1, 0}).toList();
            }
        };
        // 维度不符属于向量路失败：降级关键词并记录原因，不静默吞掉
        RetrievalService.HybridRetrievalResult result =
                service(wrongDim).retrieveHybrid("alpha 查询", null, null);
        assertThat(result.fallbacks())
                .containsExactly(com.obsidianmind.retrieval.HybridRetriever.FALLBACK_VECTOR_TO_KEYWORD);
    }
}
