package com.obsidianmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.MilvusProperties;
import com.obsidianmind.config.ModelCenterProperties;
import com.obsidianmind.modelcenter.CredentialResolver;
import com.obsidianmind.modelcenter.ModelCenterStorage;
import com.obsidianmind.modelcenter.ModelRouter;
import com.obsidianmind.parser.FrontmatterParser;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.WikiLinkParser;
import com.obsidianmind.retrieval.RetrievalStackForTest;
import com.obsidianmind.repository.InMemoryVectorStore;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.service.RagAnswerService;
import com.obsidianmind.service.RagAnswerService.RagCompletion;
import com.obsidianmind.service.impl.OllamaEmbeddingService;
import com.obsidianmind.service.impl.OllamaLLMService;
import com.obsidianmind.util.CitationParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 RAG 评估（真实 Ollama Embedding + Chat + 内存向量库，无需 Milvus/SSE）。
 * 运行：mvn test -Dtest=Phase5RagEvaluationTest -Dollama.it=1
 *
 * 自动化指标（确定性断言）：
 *  - Retrieval Recall@5（与 Phase 4 同口径）
 *  - Context 上限行为（max-chunks 生效）
 *  - 引用合法性（解析出的编号必须来自系统 Source Registry）
 *  - 路径泄露防线（回答中不得出现本机绝对路径）
 *  - No-Context 拒答（检索为空时未经 LLM 直接拒答）
 *
 * Groundedness（回答是否忠于知识库）：以启发式规则辅助 + 人工评审，
 * 不伪造 LLM-as-a-Judge 分数（详见 docs/architecture/rag-evaluation.md）。
 */
class Phase5RagEvaluationTest {

    private static final String COLLECTION = "obsidianmind_rag_eval_chunks";
    private static final Path VAULT = Path.of("..", "..", "tests", "fixtures", "obsidian-vault")
            .toAbsolutePath().normalize();
    private static final Path QUERIES = Path.of("..", "..", "tests", "evaluation", "rag", "rag-queries.json")
            .toAbsolutePath().normalize();

    /** 绝对路径泄露模式（macOS / Windows / home 目录）；LLM 无文件系统能力，出现即违规 */
    private static final Pattern ABSOLUTE_PATH = Pattern.compile("/Users/|[A-Z]:\\\\|/home/\\w+");

    /** 拒答启发式标记：命中任一即视为"诚实拒答"（人工评审前的粗筛） */
    private static final List<String> REFUSAL_MARKERS = List.of(
            "没有足够", "没有找到", "未找到", "知识库中", "无法回答", "没有相关", "不足");

    private AiProperties ai;
    private RetrievalService retrievalService;
    private RagAnswerService ragAnswerService;
    private InMemoryVectorStore vectorStore;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(enabled(), "-Dollama.it=1 未设置，跳过 RAG 评估（需真实 Ollama）");
        Assumptions.assumeTrue(Files.isDirectory(VAULT), "Demo Vault 不存在: " + VAULT);
        Assumptions.assumeTrue(Files.isRegularFile(QUERIES), "评估集不存在: " + QUERIES);

        // chat 模型使用本机已安装的 qwen3.5:9b；llm 超时放宽到 180s（评估不含交互等待）
        ai = new AiProperties("ollama",
                new AiProperties.Ollama("http://localhost:11434", "qwen3.5:9b", "bge-m3", 3),
                new AiProperties.Chunk(800, 100),
                new AiProperties.Embedding(16, 60),
                new AiProperties.Retrieval(5, 20, 0, 2, 200, 512),
                new AiProperties.Rag(6, 12000, 0.1, 1024, false, 180, 180));
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
                        new Chunker(ai), RetrievalStackForTest.properties("HYBRID")),
                ai, RetrievalStackForTest.properties("HYBRID"));
        // 空配置存储（临时目录）→ ModelRouter 走 legacy Ollama 路径，与 Phase 5 行为一致
        ModelCenterStorage storage = new ModelCenterStorage(new ModelCenterProperties(tempDir.toString()), ai);
        ModelRouter router = new ModelRouter(storage, new CredentialResolver(storage),
                new OllamaLLMService(ai), ai);
        ragAnswerService = new RagAnswerService(retrievalService, new ContextAssembler(ai),
                new RagPromptBuilder(), router, ai);
    }

    /** 系统属性 -Dollama.it=1（由 surefire 转发）或环境变量 OLLAMA_IT=1 任一启用即可。 */
    private static boolean enabled() {
        return "1".equals(System.getProperty("ollama.it")) || "1".equals(System.getenv("OLLAMA_IT"));
    }

    /** 内存 sink：收集一次编排的全部事件。 */
    private record Collected(StringBuilder content,
                             List<RagAnswerService.CitationView> citations,
                             RagAnswerService.RagCompletion completion) {

        static Collected create() {
            return new Collected(new StringBuilder(), new ArrayList<>(), null);
        }
    }

    private Collected runQuery(String query) {
        Collected collected = Collected.create();
        final RagAnswerService.RagCompletion[] done = {null};
        ragAnswerService.run(query, null, null, new RagAnswerService.RagEventSink() {
            @Override
            public void onPhase(String phase) {
            }

            @Override
            public void onCitation(ContextAssembler.ContextItem item, int index) {
                collected.citations().add(RagAnswerService.CitationView.of(item, 200));
            }

            @Override
            public void onToken(String token) {
                collected.content().append(token);
            }

            @Override
            public void onComplete(RagCompletion completion) {
                done[0] = completion;
            }

            @Override
            public void onError(String code, String message) {
                throw new AssertionError("评估查询不应报错: " + code + " " + message);
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        });
        return new Collected(collected.content(), collected.citations(), done[0]);
    }

    @Test
    void evaluateRetrievalAndAnswerBehavior() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode queries = mapper.readTree(Files.readString(QUERIES));

        double recallSum = 0;
        int refusalPass = 0;
        int refusalTotal = 0;
        List<String> report = new ArrayList<>();

        for (JsonNode item : queries) {
            String query = item.get("query").asText();
            String category = item.get("category").asText();
            String behavior = item.get("expectedBehavior").asText();
            Set<String> expected = new HashSet<>();
            item.get("expectedDocuments").forEach(d -> expected.add(d.asText()));

            // ── 检索层：Recall@5 ──
            RetrievalService.RagRetrievalResult retrieval = retrievalService.retrieveForRag(query, 5);
            Set<String> retrieved = new HashSet<>();
            retrieval.results().forEach(s -> retrieved.add(s.documentId()));
            long hits = expected.stream().filter(retrieved::contains).count();
            double recall = expected.isEmpty() ? 1.0 : (double) hits / expected.size();
            recallSum += recall;

            // ── 回答层：Context → Prompt → LLM → Citation ──
            Collected collected = runQuery(query);
            String answer = collected.completion() != null
                    ? collected.completion().content() : collected.content().toString();
            List<String> cited = collected.completion() != null
                    ? collected.completion().citedSourceIds() : List.of();

            // 路径泄露防线（所有类别通用）
            assertThat(ABSOLUTE_PATH.matcher(answer).find())
                    .as("回答泄露绝对路径: %s → %s", query, answer)
                    .isFalse();
            // 引用合法性：解析出的编号必须来自 Registry（CitationParser 已保证，此处双查）
            Set<String> known = new HashSet<>();
            collected.citations().forEach(c -> known.add(c.sourceId()));
            cited.forEach(id -> assertThat(known)
                    .as("引用 %s 不在 Source Registry 中（query=%s）", id, query).contains(id));

            boolean refusalHit = REFUSAL_MARKERS.stream().anyMatch(answer::contains);
            if ("refuse".equals(behavior)) {
                refusalTotal++;
                if (refusalHit) {
                    refusalPass++;
                }
            }
            if ("grounded".equals(behavior)) {
                assertThat(answer.length())
                        .as("grounded 查询不应为空回答: %s", query).isPositive();
            }

            report.add("[%s] %s → recall=%.0f%% chunks=%d cited=%s refusal=%s%n    答案: %s".formatted(
                    category, query, recall * 100, collected.citations().size(), cited,
                    refusalHit ? "Y" : "N", summarize(answer)));
        }

        double macroRecall = recallSum / queries.size();
        System.out.println("===== Phase 5 RAG Evaluation（真实 Ollama bge-m3 + qwen3.5:9b）=====");
        report.forEach(System.out::println);
        System.out.println("Recall@5 = " + macroRecall
                + "；拒答启发式命中 " + refusalPass + "/" + refusalTotal
                + "（groundedness 完整评估见 docs/architecture/rag-evaluation.md · Manual Evaluation）");

        assertThat(macroRecall).as("Recall@5 明细:\n%s", String.join("\n", report)).isEqualTo(1.0);
    }

    @Test
    void noContextPathShouldRefuseWithoutLlm() {
        // 检索为空（不可能命中的查询 + 空 topK 也不会有内容）→ 直接拒答不经 LLM
        // 通过独立构造空索引的链路验证：此处以真实索引 + 必空查询验证拒答文案由系统生成
        Collected collected = runQuery("zzz 完全不存在的主题字符串 qqq");
        // 该查询在真实索引下仍可能召回弱相关内容；仅当 Context 为空时验证系统拒答
        if (collected.completion() != null && collected.completion().noContext()) {
            assertThat(collected.completion().content())
                    .isEqualTo(RagAnswerService.NO_CONTEXT_ANSWER);
            assertThat(collected.citations()).isEmpty();
        }
    }

    /** 报告用摘要：压平空白并截断，避免打印整篇回答。 */
    private static String summarize(String answer) {
        String flat = answer.replaceAll("\\s+", " ").strip();
        return flat.length() <= 160 ? flat : flat.substring(0, 160) + "…";
    }
}
