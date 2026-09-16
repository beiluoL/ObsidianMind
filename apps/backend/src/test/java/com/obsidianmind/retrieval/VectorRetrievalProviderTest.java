package com.obsidianmind.retrieval;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.exception.OllamaUnavailableException;
import com.obsidianmind.repository.InMemoryVectorStore;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.service.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 向量检索 Provider 测试：正常召回、空结果、Embedding 失败传播、维度校验。
 */
class VectorRetrievalProviderTest {

    private static final String COLLECTION = "c";
    private static final int DIM = 4;

    @TempDir
    Path vaultDir;

    private VaultRepository vaultRepository;
    private InMemoryVectorStore vectorStore;

    @BeforeEach
    void setUp() throws Exception {
        vaultRepository = new VaultRepository();
        vaultRepository.connect(vaultDir.toString());
        vectorStore = new InMemoryVectorStore();
        vectorStore.ensureCollection(COLLECTION, DIM);
    }

    private VectorRetrievalProvider provider(EmbeddingService embedding) {
        return new VectorRetrievalProvider(vaultRepository, embedding, vectorStore,
                new MilvusProperties("localhost", 19530, 2, COLLECTION, DIM),
                new AiProperties("ollama", null, null, null, null, null));
    }

    private static EmbeddingService stubEmbedding(java.util.function.Function<String, float[]> fn) {
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
                return texts.stream().map(fn).toList();
            }
        };
    }

    @Test
    void retrievesRankedCandidatesWithMetadata() throws Exception {
        new InMemoryVectorStore();
        vectorStore.upsert(COLLECTION, List.of(new com.obsidianmind.domain.Chunk(
                        "Java/HashMap.md#c0", vaultRepository.requireVaultId(), "Java/HashMap.md",
                        "HashMap", "HashMap", "扩容机制", 0, 0, 4, "扩容", "h")),
                List.of(new float[]{1, 0, 0, 0}));

        VectorRetrievalProvider provider = provider(stubEmbedding(t -> new float[]{1, 0, 0, 0}));
        List<RetrievalCandidate> results = provider.retrieve("HashMap 扩容", 5);

        assertThat(results).hasSize(1);
        RetrievalCandidate c = results.get(0);
        assertThat(c.chunkId()).isEqualTo("Java/HashMap.md#c0");
        assertThat(c.vectorScore()).isGreaterThan(0.99);
        assertThat(c.retrievalType()).isEqualTo(RetrievalCandidate.RetrievalType.VECTOR);
        assertThat(Double.isNaN(c.keywordScore())).isTrue(); // 分数通道语义：关键词路未参与
    }

    @Test
    void emptyCollectionReturnsEmptyResult() {
        VectorRetrievalProvider provider = provider(stubEmbedding(t -> new float[]{1, 0, 0, 0}));
        assertThat(provider.retrieve("anything", 5)).isEmpty();
    }

    @Test
    void embeddingFailurePropagatesForFallbackDecision() {
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
        assertThatThrownBy(() -> provider(failing).retrieve("q", 5))
                .isInstanceOf(OllamaUnavailableException.class);
    }

    @Test
    void dimensionMismatchIsRejected() {
        VectorRetrievalProvider provider = provider(stubEmbedding(t -> new float[]{1, 0}));
        assertThatThrownBy(() -> provider.retrieve("q", 5))
                .isInstanceOf(com.obsidianmind.exception.EmbeddingDimensionMismatchException.class);
    }

    @Test
    void vaultIsolationFiltersForeignVectors() throws Exception {
        vectorStore.upsert(COLLECTION, List.of(new com.obsidianmind.domain.Chunk(
                        "Old.md#c0", "vault-old", "Old.md", "Old", "Old", "H", 0, 0, 4, "内容", "h")),
                List.of(new float[]{1, 0, 0, 0}));
        VectorRetrievalProvider provider = provider(stubEmbedding(t -> new float[]{1, 0, 0, 0}));
        assertThat(provider.retrieve("内容", 5)).isEmpty();
    }
}
