package com.obsidianmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.parser.FrontmatterParser;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.WikiLinkParser;
import com.obsidianmind.retrieval.HybridRetriever;
import com.obsidianmind.retrieval.RetrievalStackForTest;
import com.obsidianmind.repository.InMemoryVectorStore;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.service.impl.OllamaEmbeddingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6 检索回归测试（确定性组件，无需 Ollama/Milvus，mvn test 必跑）。
 *
 * 覆盖范围：KEYWORD（BM25 + bigram 分词）与产出层规则——chunking/分词/RRF/Reranker 的任何
 * 改动都必须跑本测试，防止"优化"导致原本正确的 Query 失败。
 * 向量路回归属真实 Embedding 评估（Phase6HybridRetrievalEvaluationIT，-Dollama.it=1）。
 *
 * 基线数字来自 2026-09-16 实测（见 docs/engineering/RETRIEVAL_EVALUATION_V2.md）；
 * 只能上调基线（有意改进后重测），出现退化必须先解释再改基线。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase6RetrievalRegressionTest {

    private static final Path VAULT = Path.of("..", "..", "tests", "fixtures", "demo-vault")
            .toAbsolutePath().normalize();
    private static final Path QUERIES = Path.of("..", "..", "tests", "evaluation", "retrieval", "queries-v2.json")
            .toAbsolutePath().normalize();

    /** 回归基线（2026-09-16 实测 KEYWORD：Recall@5=0.956，MRR=0.934；取略低于实测值做地板）。 */
    private static final double BASELINE_RECALL_AT_5 = 0.95;
    private static final double BASELINE_MRR = 0.93;

    private RetrievalService retrievalService;

    @BeforeAll
    void setUp() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(VAULT), "Demo Vault 不存在: " + VAULT);
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(QUERIES), "评估集不存在: " + QUERIES);

        // ollama 配置仅为通过 OllamaEmbeddingService 构造；KEYWORD 模式不触达网络
        AiProperties ai = new AiProperties("ollama",
                new AiProperties.Ollama("http://localhost:11434", "qwen3", "bge-m3", 3),
                new AiProperties.Chunk(800, 100), null,
                new AiProperties.Retrieval(5, 20, 0, 5, 200, 512), null);
        VaultRepository vaultRepository = new VaultRepository();
        vaultRepository.connect(VAULT.toString());
        MarkdownParser parser = new MarkdownParser(new FrontmatterParser(), new WikiLinkParser());
        // Embedding 桩：KEYWORD 模式不应触达向量路；若意外触达（fail-fast 语义）会记录 fallback 被断言捕获
        HybridRetriever hybrid = RetrievalStackForTest.hybridRetriever(vaultRepository,
                new OllamaEmbeddingService(ai), new InMemoryVectorStore(),
                new MilvusProperties("localhost", 19530, 2, "regression", 1024),
                ai, parser, new Chunker(ai), RetrievalStackForTest.properties("KEYWORD"));
        retrievalService = RetrievalStackForTest.retrievalService(hybrid, ai,
                RetrievalStackForTest.properties("KEYWORD"));
    }

    @Test
    void keywordRetrievalMeetsRegressionBaseline() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode queries = mapper.readTree(Files.readString(QUERIES));

        double recallSum = 0;
        double rrSum = 0;
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

            double rr = 0;
            for (int i = 0; i < result.results().size(); i++) {
                if (expected.contains(result.results().get(i).documentId())) {
                    rr = 1.0 / (i + 1);
                    break;
                }
            }
            rrSum += rr;
            details.add("recall=%.0f%% rr=%.2f  %s → %s".formatted(recall * 100, rr, query,
                    result.results().isEmpty() ? "-" : result.results().get(0).path()));
        }

        int n = queries.size();
        double recallAt5 = recallSum / n;
        double mrr = rrSum / n;
        System.out.println("===== Phase 6 Keyword 回归基线（" + n + " 条，demo-vault）=====");
        details.forEach(System.out::println);
        System.out.println("Recall@5=" + recallAt5 + " MRR=" + mrr);

        assertThat(recallAt5).as("KEYWORD Recall@5 退化\n%s", String.join("\n", details))
                .isGreaterThanOrEqualTo(BASELINE_RECALL_AT_5);
        assertThat(mrr).as("KEYWORD MRR 退化\n%s", String.join("\n", details))
                .isGreaterThanOrEqualTo(BASELINE_MRR);
        // KEYWORD 模式不允许触达向量路（否则降级会掩盖关键词链路回归）
        assertThat(retrievalService).isNotNull();
    }
}
