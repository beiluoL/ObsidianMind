package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.repository.InMemoryVectorStore;
import com.obsidianmind.repository.VaultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KnowledgeIndexService 增量索引状态机测试：INDEXED / SKIPPED / UPDATED / DELETED / FAILED。
 * 外部依赖全部替换：Vault 用 @TempDir 真实小文件，Embedding 用确定性桩，向量库用内存实现。
 */
class KnowledgeIndexServiceTest {

    @TempDir
    Path vaultDir;

    private final InMemoryVectorStore vectorStore = new InMemoryVectorStore();
    private final VaultRepository vaultRepository = new VaultRepository();

    private KnowledgeIndexService service(EmbeddingService embedding) {
        AiProperties ai = new AiProperties("ollama",
                new AiProperties.Ollama("http://localhost:11434", "m", "test-embed", 3),
                new AiProperties.Chunk(500, 50),
                new AiProperties.Embedding(16, 5),
                new AiProperties.Retrieval(5, 20, 0, 2, 200, 512), null);
        vaultRepository.connect(vaultDir.toString());
        return new KnowledgeIndexService(
                vaultRepository,
                new MarkdownParser(new com.obsidianmind.parser.FrontmatterParser(),
                        new com.obsidianmind.parser.WikiLinkParser()),
                new Chunker(ai),
                embedding,
                vectorStore,
                new MilvusProperties("localhost", 19530, 2, "obsidianmind_chunks", 4),
                ai);
    }

    /** 确定性 Embedding 桩：向量由文本长度决定，维度 4。 */
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
                return texts.stream()
                        .map(t -> new float[]{t.length(), 1, 0, 0})
                        .toList();
            }
        };
    }

    private static EmbeddingService failingEmbedding() {
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
                return false;
            }

            @Override
            public List<float[]> embed(List<String> texts) {
                throw new com.obsidianmind.exception.OllamaUnavailableException("Ollama down");
            }
        };
    }

    private void writeFile(String relative, String content) throws Exception {
        Path file = vaultDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void firstRunIndexesThenSecondRunSkipsAll() throws Exception {
        writeFile("Java/HashMap.md", "# HashMap\n\n基于哈希表的 Map 实现。[[ConcurrentHashMap]]");
        writeFile("README.md", "# Demo\n\n演示知识库。");
        KnowledgeIndexService service = service(stubEmbedding());

        KnowledgeIndexService.IndexResult first = service.run();
        assertThat(first.total()).isEqualTo(2);
        assertThat(first.indexed()).isEqualTo(2);
        assertThat(first.skipped()).isZero();
        assertThat(first.failed()).isZero();
        assertThat(first.chunkCount()).isGreaterThanOrEqualTo(2);
        assertThat(vectorStore.count("obsidianmind_chunks")).isEqualTo(first.chunkCount());

        KnowledgeIndexService.IndexResult second = service.run();
        assertThat(second.skipped()).isEqualTo(2);
        assertThat(second.indexed()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.chunkCount()).isZero(); // 未变化 → 不重嵌
        assertThat(vectorStore.count("obsidianmind_chunks")).isEqualTo(first.chunkCount());
    }

    @Test
    void modifiedFileIsReindexedAndOldVectorsReplaced() throws Exception {
        writeFile("Java/HashMap.md", "# HashMap v1\n\n初始内容。");
        KnowledgeIndexService service = service(stubEmbedding());
        KnowledgeIndexService.IndexResult first = service.run();
        long vectorsAfterFirst = vectorStore.count("obsidianmind_chunks");
        assertThat(first.indexed()).isEqualTo(1);

        Files.writeString(vaultDir.resolve("Java/HashMap.md"), "# HashMap v2\n\n修改后的更长的正文内容，切块数量可能与之前不同。\n\n## 新增节\n\n新段落。");
        KnowledgeIndexService.IndexResult second = service.run();
        assertThat(second.updated()).isEqualTo(1);
        assertThat(second.indexed()).isZero();
        // 旧向量被文档级 replace 清理，不残留
        assertThat(vectorStore.count("obsidianmind_chunks")).isEqualTo(second.chunkCount());
        assertThat(vectorsAfterFirst).isNotEqualTo(vectorStore.count("obsidianmind_chunks"));
    }

    @Test
    void deletedFileVectorsAreRemoved() throws Exception {
        writeFile("Java/HashMap.md", "# HashMap\n\n内容。");
        writeFile("Java/Gone.md", "# Gone\n\n将被删除。");
        KnowledgeIndexService service = service(stubEmbedding());
        service.run();
        long before = vectorStore.count("obsidianmind_chunks");
        assertThat(before).isGreaterThan(0);

        Files.delete(vaultDir.resolve("Java/Gone.md"));
        KnowledgeIndexService.IndexResult result = service.run();
        assertThat(result.deleted()).isEqualTo(1);
        // Gone.md 的向量被清理（文档级 delete），HashMap.md 的不受影响
        assertThat(vectorStore.count("obsidianmind_chunks")).isEqualTo(before - 1);
        assertThat(vectorStore.loadDocumentHashes("obsidianmind_chunks", vaultRepository.requireVaultId()))
                .doesNotContainKey("Java/Gone.md");
    }

    @Test
    void embeddingFailureMarksFilesFailedWithoutSilentSuccess() throws Exception {
        writeFile("Java/A.md", "# A\n\n内容甲。");
        writeFile("Java/B.md", "# B\n\n内容乙。");
        KnowledgeIndexService service = service(failingEmbedding());

        KnowledgeIndexService.IndexResult result = service.run();
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors()).allSatisfy(e ->
                assertThat(e.code()).isEqualTo(KnowledgeIndexService.CODE_EMBEDDING));
        assertThat(result.indexed()).isZero();
        assertThat(vectorStore.count("obsidianmind_chunks")).isZero();
    }

    @Test
    void dimensionMismatchThrowsConfigurationError() throws Exception {
        writeFile("Java/A.md", "# A\n\n内容。");
        // 维度 3 的向量，但配置要求 4 → 503 EmbeddingException
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
                return texts.stream().map(t -> new float[]{1, 0, 0}).toList();
            }
        };
        KnowledgeIndexService service = service(wrongDim);
        assertThatThrownBy(service::run)
                .isInstanceOf(com.obsidianmind.exception.EmbeddingException.class)
                .hasMessageContaining("维度不符");
    }

    @Test
    void malformedMarkdownStillIndexesAsBody() throws Exception {
        writeFile("Java/Broken.md", "---\n不是合法 frontmatter: [未闭合\n---\n# Broken\n\n正文仍可解析。");
        KnowledgeIndexService service = service(stubEmbedding());
        KnowledgeIndexService.IndexResult result = service.run();
        // 畸形 frontmatter 降级为纯文本处理（Parser 既有行为），不产生 FAILED
        assertThat(result.failed()).isZero();
        assertThat(result.indexed()).isEqualTo(1);
        assertThat(result.chunkCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void emptyBodyDocumentIndexesWithZeroChunks() throws Exception {
        // 纯 frontmatter 文件：body 为空 → 0 Chunk，是正常状态而非错误
        writeFile("Java/Empty.md", "---\ntitle: 空文档\n---\n");
        KnowledgeIndexService service = service(stubEmbedding());
        KnowledgeIndexService.IndexResult result = service.run();
        assertThat(result.failed()).isZero();
        assertThat(result.chunkCount()).isZero();
        assertThat(vectorStore.count("obsidianmind_chunks")).isZero();
    }
}
