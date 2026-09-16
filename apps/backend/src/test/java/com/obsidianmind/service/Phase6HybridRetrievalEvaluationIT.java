package com.obsidianmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.parser.FrontmatterParser;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.WikiLinkParser;
import com.obsidianmind.retrieval.HybridRetriever;
import com.obsidianmind.retrieval.RetrievalMode;
import com.obsidianmind.retrieval.RetrievalStackForTest;
import com.obsidianmind.repository.InMemoryVectorStore;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.service.impl.OllamaEmbeddingService;
import org.junit.jupiter.api.Assumptions;
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
 * Phase 6 Hybrid Retrieval 评估（真实 Ollama Embedding + 内存向量库，无需 Milvus）。
 * 运行：mvn test -Dtest=Phase6HybridRetrievalEvaluationIT -Dollama.it=1
 *
 * 四组对比（同一数据集 tests/evaluation/retrieval/queries-v2.json，34 条）：
 *   Run A：Vector Only
 *   Run B：Keyword Only（BM25 + bigram）
 *   Run C：Hybrid（RRF，Reranker 未启用）
 *   Run D：Hybrid + Reranker（确定性词法 Reranker——非神经模型，如实标注）
 * 指标：Recall@1/3/5、MRR、各阶段耗时。结果写入 docs/engineering/RETRIEVAL_EVALUATION_V2.md。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase6HybridRetrievalEvaluationIT {

    private static final String COLLECTION = "obsidianmind_eval_chunks";
    private static final Path VAULT = Path.of("..", "..", "tests", "fixtures", "demo-vault")
            .toAbsolutePath().normalize();
    private static final Path QUERIES = Path.of("..", "..", "tests", "evaluation", "retrieval", "queries-v2.json")
            .toAbsolutePath().normalize();

    private VaultRepository vaultRepository;
    private RetrievalService retrievalService;
    private RetrievalPropertiesHolder props;

    /** 测试内可切换 Reranker 开关的配置持有者。 */
    private record RetrievalPropertiesHolder(
            com.obsidianmind.config.RetrievalProperties withReranker,
            com.obsidianmind.config.RetrievalProperties withoutReranker) {
    }

    @BeforeAll
    void setUp() throws Exception {
        Assumptions.assumeTrue(enabled(), "-Dollama.it=1 未设置，跳过 Hybrid 检索评估（需真实 Ollama）");
        Assumptions.assumeTrue(Files.isDirectory(VAULT), "Demo Vault 不存在: " + VAULT);
        Assumptions.assumeTrue(Files.isRegularFile(QUERIES), "评估集不存在: " + QUERIES);

        AiProperties ai = new AiProperties("ollama",
                new AiProperties.Ollama("http://localhost:11434", "qwen3.5:9b", "bge-m3", 3),
                new AiProperties.Chunk(800, 100),
                new AiProperties.Embedding(16, 60),
                new AiProperties.Retrieval(5, 20, 0, 5, 200, 512), null);
        vaultRepository = new VaultRepository();
        vaultRepository.connect(VAULT.toString());
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        MarkdownParser parser = new MarkdownParser(new FrontmatterParser(), new WikiLinkParser());
        KnowledgeIndexService indexService = new KnowledgeIndexService(
                vaultRepository, parser, new Chunker(ai), new OllamaEmbeddingService(ai),
                vectorStore, new MilvusProperties("localhost", 19530, 2, COLLECTION, 1024), ai);
        KnowledgeIndexService.IndexResult indexed = indexService.run();
        Assumptions.assumeTrue(indexed.failed() == 0, "索引存在失败，无法评估: " + indexed.errors());

        props = new RetrievalPropertiesHolder(
                withReranker(true), withReranker(false));
        retrievalService = RetrievalStackForTest.retrievalService(
                RetrievalStackForTest.hybridRetriever(vaultRepository, new OllamaEmbeddingService(ai),
                        vectorStore, new MilvusProperties("localhost", 19530, 2, COLLECTION, 1024),
                        ai, parser, new Chunker(ai), props.withoutReranker()),
                ai, props.withoutReranker());
        System.out.println("===== 索引完成: " + vectorStore.count(COLLECTION) + " chunks =====");
    }

    private static com.obsidianmind.config.RetrievalProperties withReranker(boolean enabled) {
        return new com.obsidianmind.config.RetrievalProperties("HYBRID",
                new com.obsidianmind.config.RetrievalProperties.Rrf(60),
                new com.obsidianmind.config.RetrievalProperties.Candidates(20, 20),
                new com.obsidianmind.config.RetrievalProperties.Keyword(1.5, 0.75),
                new com.obsidianmind.config.RetrievalProperties.Reranker(enabled, "lexical", "", 20),
                false);
    }

    private static boolean enabled() {
        return "1".equals(System.getProperty("ollama.it")) || "1".equals(System.getenv("OLLAMA_IT"));
    }

    private record Metrics(int recallAt1Hits, int recallAt3Hits, int recallAt5Hits,
                           int expectedTotal, double rrSum, long totalMs) {
    }

    private record QueryResult(List<String> retrievedDocs, int expectedSize, Set<String> expected) {
    }

    @Test
    void compareVectorKeywordHybridAndReranker() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode queries = mapper.readTree(Files.readString(QUERIES));
        int n = queries.size();

        // ── Run A/B/C：通过 retrieveHybrid 的模式切换实现同一套产出层规则 ──
        Metrics vector = runMode(queries, RetrievalMode.VECTOR, false);
        Metrics keyword = runMode(queries, RetrievalMode.KEYWORD, false);
        Metrics hybrid = runMode(queries, RetrievalMode.HYBRID, false);
        // ── Run D：Hybrid + Reranker（词法确定性 Reranker，非神经模型）──
        Metrics hybridRerank = runWithReranker(queries);

        System.out.println("===== Phase 6 Hybrid Retrieval Evaluation（" + n
                + " queries，demo-vault，bge-m3）=====");
        System.out.printf("| Mode | Recall@1 | Recall@3 | Recall@5 | MRR | avgMs |%n");
        printRow("Vector", vector, n);
        printRow("Keyword", keyword, n);
        printRow("Hybrid", hybrid, n);
        printRow("Hybrid+LexRerank", hybridRerank, n);

        // 完整性断言（不预设 Hybrid 一定更好——结果如实记录进评估报告）
        assertThat(vector.expectedTotal()).isEqualTo(n);
        assertThat(keyword.expectedTotal()).isEqualTo(n);
        assertThat(hybrid.expectedTotal()).isEqualTo(n);
        assertThat(hybridRerank.expectedTotal()).isEqualTo(n);
    }

    private Metrics runMode(JsonNode queries, RetrievalMode mode, boolean unused) throws Exception {
        double rrSum = 0;
        int r1 = 0;
        int r3 = 0;
        int r5 = 0;
        long totalMs = 0;
        List<String> degraded = new ArrayList<>();
        for (JsonNode item : queries) {
            String query = item.get("query").asText();
            Set<String> expected = new HashSet<>();
            item.get("expectedDocuments").forEach(d -> expected.add(d.asText()));

            RetrievalService.HybridRetrievalResult result =
                    retrievalService.retrieveHybrid(query, mode, 5);
            List<String> docs = result.results().stream().map(RetrievalService.Source::documentId).toList();
            long ms = result.elapsedMs();
            totalMs += ms;

            if (result.results().size() >= 1 && expected.contains(docs.get(0))) {
                r1++;
            }
            if (anyHit(expected, docs, 3)) {
                r3++;
            }
            if (anyHit(expected, docs, 5)) {
                r5++;
            }
            rrSum += reciprocalRank(expected, docs);
            if (reciprocalRank(expected, docs) == 0) {
                degraded.add("[" + mode + "] MISS " + query);
            } else if (reciprocalRank(expected, docs) < 1.0) {
                degraded.add("[" + mode + "] TOP1-MISS " + query + " → " + (docs.isEmpty() ? "-" : docs.get(0)));
            }
        }
        degraded.forEach(System.out::println);
        return new Metrics(r1, r3, r5, queries.size(), rrSum, totalMs);
    }

    /** Run D：重建启用 Reranker 的检索服务后按 HYBRID 模式跑。 */
    private Metrics runWithReranker(JsonNode queries) throws Exception {
        AiProperties ai = new AiProperties("ollama",
                new AiProperties.Ollama("http://localhost:11434", "qwen3.5:9b", "bge-m3", 3),
                new AiProperties.Chunk(800, 100),
                new AiProperties.Embedding(16, 60),
                new AiProperties.Retrieval(5, 20, 0, 5, 200, 512), null);
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        MarkdownParser parser = new MarkdownParser(new FrontmatterParser(), new WikiLinkParser());
        KnowledgeIndexService indexService = new KnowledgeIndexService(
                vaultRepository, parser, new Chunker(ai), new OllamaEmbeddingService(ai),
                vectorStore, new MilvusProperties("localhost", 19530, 2, COLLECTION + "_rerank", 1024), ai);
        Assumptions.assumeTrue(indexService.run().failed() == 0, "Reranker 组索引失败");
        RetrievalService withRerank = RetrievalStackForTest.retrievalService(
                RetrievalStackForTest.hybridRetriever(vaultRepository, new OllamaEmbeddingService(ai),
                        vectorStore, new MilvusProperties("localhost", 19530, 2, COLLECTION + "_rerank", 1024),
                        ai, parser, new Chunker(ai), props.withReranker()),
                ai, props.withReranker());

        double rrSum = 0;
        int r1 = 0;
        int r3 = 0;
        int r5 = 0;
        long totalMs = 0;
        List<String> degraded = new ArrayList<>();
        for (JsonNode item : queries) {
            String query = item.get("query").asText();
            Set<String> expected = new HashSet<>();
            item.get("expectedDocuments").forEach(d -> expected.add(d.asText()));

            RetrievalService.HybridRetrievalResult result = withRerank.retrieveHybrid(query, RetrievalMode.HYBRID, 5);
            List<String> docs = result.results().stream().map(RetrievalService.Source::documentId).toList();
            totalMs += result.elapsedMs();
            if (!docs.isEmpty() && expected.contains(docs.get(0))) {
                r1++;
            }
            if (anyHit(expected, docs, 3)) {
                r3++;
            }
            if (anyHit(expected, docs, 5)) {
                r5++;
            }
            rrSum += reciprocalRank(expected, docs);
            if (reciprocalRank(expected, docs) == 0) {
                degraded.add("[Hybrid+Rerank] MISS " + query);
            } else if (reciprocalRank(expected, docs) < 1.0) {
                degraded.add("[Hybrid+Rerank] TOP1-MISS " + query + " → " + (docs.isEmpty() ? "-" : docs.get(0)));
            }
        }
        degraded.forEach(System.out::println);
        return new Metrics(r1, r3, r5, queries.size(), rrSum, totalMs);
    }

    private static boolean anyHit(Set<String> expected, List<String> docs, int k) {
        return docs.stream().limit(k).anyMatch(expected::contains);
    }

    private static double reciprocalRank(Set<String> expected, List<String> docs) {
        for (int i = 0; i < docs.size(); i++) {
            if (expected.contains(docs.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0;
    }

    private static void printRow(String name, Metrics m, int n) {
        System.out.printf("| %s | %d/%d (%.1f%%) | %d/%d (%.1f%%) | %d/%d (%.1f%%) | %.3f | %d |%n",
                name,
                m.recallAt1Hits(), n, 100.0 * m.recallAt1Hits() / n,
                m.recallAt3Hits(), n, 100.0 * m.recallAt3Hits() / n,
                m.recallAt5Hits(), n, 100.0 * m.recallAt5Hits() / n,
                m.rrSum() / n, m.totalMs() / n);
    }
}
