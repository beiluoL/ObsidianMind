package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.parser.FrontmatterParser;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.WikiLinkParser;
import com.obsidianmind.repository.InMemoryVectorStore;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.service.impl.OllamaEmbeddingService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 管线端到端验证（真实 Ollama Embedding + 内存向量库）。
 * 仅在 -Dollama.it=1（surefire 转发）或环境变量 OLLAMA_IT=1 时执行，默认自动跳过。
 * 运行：mvn test -Dtest=Phase3PipelineVerificationTest -Dollama.it=1
 * Milvus 实机验证见 MilvusVectorStoreIT（MILVUS_IT=1 条件启用）。
 */
class Phase3PipelineVerificationTest {

    private static final String COLLECTION = "obsidianmind_it_demo";
    private InMemoryVectorStore vectorStore;
    private KnowledgeIndexService service;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(enabled(), "OLLAMA_IT=1 / -Dollama.it=1 未设置，跳过真实 Ollama 管线验证");
        Path vaultPath = Path.of("..", "..", "tests", "fixtures", "obsidian-vault").toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isDirectory(vaultPath), "Demo Vault 不存在: " + vaultPath);

        AiProperties ai = new AiProperties("ollama",
                new AiProperties.Ollama("http://localhost:11434", "qwen3", "bge-m3", 3),
                new AiProperties.Chunk(800, 100),
                new AiProperties.Embedding(16, 60),
                new AiProperties.Retrieval(5, 20, 0, 2, 200, 512), null);
        VaultRepository vaultRepository = new VaultRepository();
        vaultRepository.connect(vaultPath.toString());
        vectorStore = new InMemoryVectorStore();
        service = new KnowledgeIndexService(
                vaultRepository,
                new MarkdownParser(new FrontmatterParser(), new WikiLinkParser()),
                new Chunker(ai),
                new OllamaEmbeddingService(ai),
                vectorStore,
                new MilvusProperties("localhost", 19530, 2, COLLECTION, 1024),
                ai);
    }

    /** 系统属性 -Dollama.it=1（由 surefire 转发）或环境变量 OLLAMA_IT=1 任一启用即可。 */
    private static boolean enabled() {
        return "1".equals(System.getProperty("ollama.it")) || "1".equals(System.getenv("OLLAMA_IT"));
    }

    @Test
    void demoVaultEndToEndCountChain() {
        long markdownFiles = 0;
        try (var files = Files.walk(Path.of("..", "..", "tests", "fixtures", "obsidian-vault"))) {
            markdownFiles = files.filter(p -> p.toString().endsWith(".md")).count();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        KnowledgeIndexService.IndexResult first = service.run();
        System.out.println("===== Phase 3 Demo Vault 验证 =====");
        System.out.println("Markdown: " + first.total());
        System.out.println("Indexed(新增): " + first.indexed() + ", Updated: " + first.updated()
                + ", Skipped: " + first.skipped() + ", Deleted: " + first.deleted() + ", Failed: " + first.failed());
        System.out.println("Chunks(=Embeddings=Vectors): " + first.chunkCount());
        System.out.println("InMemoryVectorStore count: " + vectorStore.count(COLLECTION));
        System.out.println("Errors: " + first.errors());

        // 数量链必须可以相互解释：8 Markdown → 8 Documents → N Chunks → N Embeddings → N Vectors
        assertThat(first.failed()).as("不允许失败: %s", first.errors()).isZero();
        assertThat(first.total()).isEqualTo(markdownFiles);
        assertThat(first.indexed()).isEqualTo(markdownFiles);
        assertThat(first.chunkCount()).isGreaterThanOrEqualTo((int) markdownFiles); // 每篇至少 1 Chunk
        assertThat(vectorStore.count(COLLECTION)).isEqualTo(first.chunkCount());

        // 增量语义：第二次同步全部 SKIPPED，向量数不变
        KnowledgeIndexService.IndexResult second = service.run();
        assertThat(second.skipped()).isEqualTo(markdownFiles);
        assertThat(second.indexed()).isZero();
        assertThat(second.chunkCount()).isZero();
        assertThat(vectorStore.count(COLLECTION)).isEqualTo(first.chunkCount());
    }
}
