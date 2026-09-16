package com.obsidianmind.retrieval;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.exception.RetrievalUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HybridRetriever 编排测试（Provider 用可控 Fake，不依赖 Milvus/Ollama）：
 * 融合、单/双向降级、两路全败、Reranker NOT_CONFIGURED 与 APPLIED 语义。
 */
class HybridRetrieverTest {

    private static RetrievalCandidate vectorCandidate(String id, double score) {
        return new RetrievalCandidate(id, id, id + ".md", id, "H", 0, "content " + id,
                score, Double.NaN, Double.NaN, Double.NaN, RetrievalCandidate.RetrievalType.VECTOR);
    }

    private static RetrievalCandidate keywordCandidate(String id, double score) {
        return new RetrievalCandidate(id, id, id + ".md", id, "H", 0, "content " + id,
                Double.NaN, score, Double.NaN, Double.NaN, RetrievalCandidate.RetrievalType.KEYWORD);
    }

    private static RetrievalProvider providerOf(java.util.function.BiFunction<String, Integer, List<RetrievalCandidate>> fn) {
        return new RetrievalProvider() {
            @Override
            public String name() {
                return "fake";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public List<RetrievalCandidate> retrieve(String query, int candidateCount) {
                return fn.apply(query, candidateCount);
            }
        };
    }

    private static HybridRetriever retriever(RetrievalProvider vector, RetrievalProvider keyword,
                                             boolean rerankerEnabled) {
        com.obsidianmind.config.RetrievalProperties properties =
                new com.obsidianmind.config.RetrievalProperties("HYBRID",
                        new com.obsidianmind.config.RetrievalProperties.Rrf(60),
                        new com.obsidianmind.config.RetrievalProperties.Candidates(20, 20),
                        new com.obsidianmind.config.RetrievalProperties.Keyword(1.5, 0.75),
                        new com.obsidianmind.config.RetrievalProperties.Reranker(rerankerEnabled, "lexical", "", 20),
                        false);
        return new HybridRetriever(vector, keyword,
                new LexicalReranker(properties), new AiProperties("ollama", null, null, null, null, null),
                properties);
    }

    @Test
    void hybridModeFusesBothListsByRrf() {
        HybridRetriever retriever = retriever(
                providerOf((q, k) -> List.of(vectorCandidate("A", 0.9), vectorCandidate("B", 0.8))),
                providerOf((q, k) -> List.of(keywordCandidate("C", 9.0), keywordCandidate("A", 7.0))),
                false);

        HybridRetriever.RetrievalOutcome outcome = retriever.retrieve("q", RetrievalMode.HYBRID, 5);

        assertThat(outcome.effectiveMode()).isEqualTo(RetrievalMode.HYBRID);
        assertThat(outcome.fallbacks()).isEmpty();
        // A 两路命中 → 融合分最高；C keyword rank1；B vector rank2
        assertThat(outcome.finalCandidates()).extracting(RetrievalCandidate::identity)
                .containsExactly("A", "C", "B");
        assertThat(outcome.rerankerStatus()).isEqualTo(HybridRetriever.RERANKER_NOT_CONFIGURED);
    }

    @Test
    void vectorFailureInHybridDegradesToKeywordObservably() {
        HybridRetriever retriever = retriever(
                providerOf((q, k) -> {
                    throw new com.obsidianmind.exception.OllamaUnavailableException("Ollama down");
                }),
                providerOf((q, k) -> List.of(keywordCandidate("C", 9.0))),
                false);

        HybridRetriever.RetrievalOutcome outcome = retriever.retrieve("q", RetrievalMode.HYBRID, 5);

        assertThat(outcome.effectiveMode()).isEqualTo(RetrievalMode.KEYWORD);
        assertThat(outcome.fallbacks()).containsExactly(HybridRetriever.FALLBACK_VECTOR_TO_KEYWORD);
        assertThat(outcome.failureReasons()).anyMatch(r -> r.contains("Ollama down"));
        assertThat(outcome.finalCandidates()).extracting(RetrievalCandidate::identity).containsExactly("C");
    }

    @Test
    void keywordFailureInKeywordModeDegradesToVectorObservably() {
        HybridRetriever retriever = retriever(
                providerOf((q, k) -> List.of(vectorCandidate("A", 0.9))),
                providerOf((q, k) -> {
                    throw new RuntimeException("index broken");
                }),
                false);

        HybridRetriever.RetrievalOutcome outcome = retriever.retrieve("q", RetrievalMode.KEYWORD, 5);

        assertThat(outcome.effectiveMode()).isEqualTo(RetrievalMode.VECTOR);
        assertThat(outcome.fallbacks()).containsExactly(HybridRetriever.FALLBACK_KEYWORD_TO_VECTOR);
        assertThat(outcome.failureReasons()).anyMatch(r -> r.contains("index broken"));
        assertThat(outcome.finalCandidates()).extracting(RetrievalCandidate::identity).containsExactly("A");
    }

    @Test
    void bothFailuresRaiseRetrievalUnavailable() {
        HybridRetriever retriever = retriever(
                providerOf((q, k) -> {
                    throw new RuntimeException("vector down");
                }),
                providerOf((q, k) -> {
                    throw new RuntimeException("keyword down");
                }),
                false);

        assertThatThrownBy(() -> retriever.retrieve("q", RetrievalMode.HYBRID, 5))
                .isInstanceOf(RetrievalUnavailableException.class)
                .hasMessageContaining("均不可用");
    }

    @Test
    void emptyResultsAreLegitimateNotError() {
        HybridRetriever retriever = retriever(
                providerOf((q, k) -> List.of()),
                providerOf((q, k) -> List.of()),
                false);

        HybridRetriever.RetrievalOutcome outcome = retriever.retrieve("q", RetrievalMode.HYBRID, 5);

        assertThat(outcome.finalCandidates()).isEmpty();
        assertThat(outcome.fallbacks()).isEmpty(); // 空结果 ≠ 失败
    }

    @Test
    void rerankerAppliedReordersHeadAndNotConfiguredKeepsRrf() {
        // 场景：W 两路命中排第一但内容与查询无关；S 只在向量路、内容与查询词面高度吻合。
        // NOT_CONFIGURED：保持 RRF 序 [W, S, M]；APPLIED：词法重排把 S 顶到第一。
        HybridRetriever disabled = retriever(
                providerOf((q, k) -> List.of(
                        candidateWithContent("W", "完全无关的内容", 0.9, true),
                        candidateWithContent("S", "扩容机制与负载因子", 0.3, true))),
                providerOf((q, k) -> List.of(
                        candidateWithContent("W", "完全无关的内容", 7.0, false),
                        candidateWithContent("M", "也无关的内容M", 6.0, false))),
                false);
        HybridRetriever.RetrievalOutcome before = disabled.retrieve("扩容 负载", RetrievalMode.HYBRID, 5);
        assertThat(before.rerankerStatus()).isEqualTo(HybridRetriever.RERANKER_NOT_CONFIGURED);
        assertThat(before.finalCandidates()).extracting(RetrievalCandidate::identity)
                .containsExactly("W", "S", "M");

        HybridRetriever enabled = retriever(
                providerOf((q, k) -> List.of(
                        candidateWithContent("W", "完全无关的内容", 0.9, true),
                        candidateWithContent("S", "扩容机制与负载因子", 0.3, true))),
                providerOf((q, k) -> List.of(
                        candidateWithContent("W", "完全无关的内容", 7.0, false),
                        candidateWithContent("M", "也无关的内容M", 6.0, false))),
                true);
        HybridRetriever.RetrievalOutcome after = enabled.retrieve("扩容 负载", RetrievalMode.HYBRID, 5);
        assertThat(after.rerankerStatus()).isEqualTo(HybridRetriever.RERANKER_APPLIED);
        // 词面覆盖：S（两词全中）被顶到第一；候选集合不变
        assertThat(after.finalCandidates()).extracting(RetrievalCandidate::identity)
                .containsExactly("S", "W", "M");
        assertThat(after.finalCandidates()).allSatisfy(c ->
                assertThat(c.finalScore()).isGreaterThanOrEqualTo(0));
    }

    private static RetrievalCandidate candidateWithContent(String id, String content, double score,
                                                           boolean vector) {
        return new RetrievalCandidate(id, id, id + ".md", id, "H", 0, content,
                vector ? score : Double.NaN, vector ? Double.NaN : score,
                Double.NaN, Double.NaN,
                vector ? RetrievalCandidate.RetrievalType.VECTOR : RetrievalCandidate.RetrievalType.KEYWORD);
    }
}
