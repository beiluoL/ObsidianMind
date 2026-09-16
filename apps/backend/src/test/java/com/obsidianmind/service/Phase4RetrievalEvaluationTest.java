package com.obsidianmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.parser.FrontmatterParser;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.WikiLinkParser;
import com.obsidianmind.retrieval.RetrievalStackForTest;
import com.obsidianmind.repository.InMemoryVectorStore;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.service.impl.OllamaEmbeddingService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 检索评估（真实 Ollama Embedding + 内存向量库，无需 Milvus）。
 * 运行：mvn test -Dtest=Phase4RetrievalEvaluationTest -Dollama.it=1
 * 指标与数据集说明见 docs/architecture/retrieval-evaluation.md。
 */
class Phase4RetrievalEvaluationTest {

    private static final String COLLECTION = "obsidianmind_eval_chunks";
    private static final Path VAULT = Path.of("..", "..", "tests", "fixtures", "obsidian-vault")
            .toAbsolutePath().normalize();
    private static final Path QUERIES = Path.of("..", "..", "tests", "evaluation", "retrieval", "queries.json")
            .toAbsolutePath().normalize();

    private InMemoryVectorStore vectorStore;
    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(enabled(), "-Dollama.it=1 未设置，跳过检索评估（需真实 Ollama）");
        Assumptions.assumeTrue(Files.isDirectory(VAULT), "Demo Vault 不存在: " + VAULT);
        Assumptions.assumeTrue(Files.isRegularFile(QUERIES), "评估集不存在: " + QUERIES);

        AiProperties ai = new AiProperties("ollama",
                new AiProperties.Ollama("http://localhost:11434", "qwen3", "bge-m3", 3),
                new AiProperties.Chunk(800, 100),
                new AiProperties.Embedding(16, 60),
                new AiProperties.Retrieval(5, 20, 0, 2, 200, 512), null);
        VaultRepository vaultRepository = new VaultRepository();
        vaultRepository.connect(VAULT.toString());
        vectorStore = new InMemoryVectorStore();
        KnowledgeIndexService indexService = new KnowledgeIndexService(
                vaultRepository,
                new MarkdownParser(new FrontmatterParser(), new WikiLinkParser()),
                new Chunker(ai),
                new OllamaEmbeddingService(ai),
                vectorStore,
                new MilvusProperties("localhost", 19530, 2, COLLECTION, 1024),
                ai);
        KnowledgeIndexService.IndexResult indexed = indexService.run();
        Assumptions.assumeTrue(indexed.failed() == 0, "索引存在失败，无法评估: " + indexed.errors());
        retrievalService = RetrievalStackForTest.retrievalService(
                RetrievalStackForTest.hybridRetriever(vaultRepository, new OllamaEmbeddingService(ai),
                        vectorStore, new MilvusProperties("localhost", 19530, 2, COLLECTION, 1024), ai,
                        new MarkdownParser(new FrontmatterParser(), new WikiLinkParser()),
                        new Chunker(ai), RetrievalStackForTest.properties("VECTOR")),
                ai, RetrievalStackForTest.properties("VECTOR"));
    }

    /** 系统属性 -Dollama.it=1（由 surefire 转发）或环境变量 OLLAMA_IT=1 任一启用即可。 */
    private static boolean enabled() {
        return "1".equals(System.getProperty("ollama.it")) || "1".equals(System.getenv("OLLAMA_IT"));
    }

    @Test
    void recallAt5MeetsBaseline() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode queries = mapper.readTree(Files.readString(QUERIES));

        int hitQueries = 0;
        double recallSum = 0;
        List<String> details = new ArrayList<>();
        for (JsonNode item : queries) {
            String query = item.get("query").asText();
            Set<String> expected = new HashSet<>();
            item.get("expectedDocuments").forEach(d -> expected.add(d.asText()));

            RetrievalService.RetrievalResult result = retrievalService.retrieve(query, 5);
            Set<String> retrieved = new HashSet<>();
            result.results().forEach(s -> retrieved.add(s.documentId()));

            long hits = expected.stream().filter(retrieved::contains).count();
            double recall = expected.isEmpty() ? 1.0 : (double) hits / expected.size();
            recallSum += recall;
            if (recall == 1.0) {
                hitQueries++;
            }
            details.add("%.0f%%  %s → 命中 %d/%d（top1=%s score=%.4f）".formatted(
                    recall * 100, query, hits, expected.size(),
                    result.results().isEmpty() ? "-" : result.results().get(0).path(),
                    result.results().isEmpty() ? 0 : result.results().get(0).score()));
        }

        System.out.println("===== Phase 4 Retrieval Evaluation（Recall@5，真实 Ollama bge-m3）=====");
        details.forEach(System.out::println);
        double macroRecall = recallSum / queries.size();
        System.out.println("Recall@5 = " + macroRecall
                + "（" + hitQueries + "/" + queries.size() + " 条全命中，Vectors=" + vectorStore.count(COLLECTION) + "）");

        // MVP 基线：小数据集上应全命中；引入更多干扰文档后改为统计阈值并记录历史
        assertThat(macroRecall).as("Recall@5 明细:\n%s", String.join("\n", details)).isEqualTo(1.0);
    }
}
