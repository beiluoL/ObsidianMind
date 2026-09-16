package com.obsidianmind.retrieval;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LexicalReranker 测试：确定性排序、IDF 加权、禁用状态诚实（NOT_CONFIGURED 语义）、候选集合不变。
 */
class LexicalRerankerTest {

    private static RetrievalCandidate candidate(String id, String content, double rrfScore) {
        return new RetrievalCandidate(id, id, id + ".md", id, "H", 0, content,
                Double.NaN, Double.NaN, rrfScore, Double.NaN, RetrievalCandidate.RetrievalType.HYBRID);
    }

    private static LexicalReranker reranker(boolean enabled) {
        com.obsidianmind.config.RetrievalProperties properties =
                RetrievalStackForTest.properties("HYBRID");
        if (enabled) {
            properties = new com.obsidianmind.config.RetrievalProperties("HYBRID",
                    new com.obsidianmind.config.RetrievalProperties.Rrf(60),
                    new com.obsidianmind.config.RetrievalProperties.Candidates(20, 20),
                    new com.obsidianmind.config.RetrievalProperties.Keyword(1.5, 0.75),
                    new com.obsidianmind.config.RetrievalProperties.Reranker(true, "lexical", "", 20),
                    false);
        }
        return new LexicalReranker(properties);
    }

    @Test
    void disabledMeansNotAvailableAndNeverCalled() {
        LexicalReranker reranker = reranker(false);
        assertThat(reranker.isAvailable()).isFalse();
        assertThat(reranker.name()).isEqualTo("lexical");
        // HybridRetriever 的契约：不可用时不得调用 rerank
        assertThatThrownByChecked(reranker);
    }

    private void assertThatThrownByChecked(LexicalReranker reranker) {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> reranker.rerank("q", List.of(candidate("A", "content", 0.03))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT_CONFIGURED");
    }

    @Test
    void higherLexicalOverlapRanksFirst() {
        LexicalReranker reranker = reranker(true);
        List<RetrievalCandidate> candidates = new ArrayList<>(List.of(
                candidate("Weak", "完全无关的内容", 0.05),
                candidate("Strong", "HashMap 扩容机制 负载因子 resize 触发条件", 0.03),
                candidate("Partial", "HashMap 简介", 0.04)));

        List<RetrievalCandidate> reranked = reranker.rerank("HashMap 扩容", candidates);

        // 词面覆盖：Strong（两词全中）> Partial（一词）> Weak（零命中）
        assertThat(reranked).extracting(RetrievalCandidate::identity)
                .containsExactly("Strong", "Partial", "Weak");
        assertThat(reranked.get(0).rerankScore()).isGreaterThan(reranked.get(1).rerankScore());
        assertThat(reranked.get(2).rerankScore()).isEqualTo(0.0);
    }

    @Test
    void rerankKeepsCandidateSetAndIdentityUnchanged() {
        LexicalReranker reranker = reranker(true);
        List<RetrievalCandidate> candidates = List.of(
                candidate("A", "HashMap 内容", 0.03),
                candidate("B", "扩容 内容", 0.04));

        List<RetrievalCandidate> reranked = reranker.rerank("HashMap 扩容", candidates);

        assertThat(reranked).extracting(RetrievalCandidate::identity)
                .containsExactlyInAnyOrder("A", "B"); // 只重排，不增不减
        assertThat(reranked.get(0).rrfScore()).isPositive(); // rrfScore 通道保留
    }

    @Test
    void deterministicSameInputSameOutput() {
        LexicalReranker reranker = reranker(true);
        List<RetrievalCandidate> candidates = List.of(
                candidate("A", "HashMap 扩容", 0.03),
                candidate("B", "扩容 resize", 0.04),
                candidate("C", "HashMap", 0.02));

        List<RetrievalCandidate> first = reranker.rerank("HashMap 扩容", candidates);
        List<RetrievalCandidate> second = reranker.rerank("HashMap 扩容", candidates);

        assertThat(first).extracting(RetrievalCandidate::identity)
                .isEqualTo(second.stream().map(RetrievalCandidate::identity).toList());
    }

    @Test
    void emptyCandidatesReturnEmpty() {
        assertThat(reranker(true).rerank("q", List.of())).isEmpty();
    }

    @Test
    void queryWithoutValidTokensKeepsOriginalOrder() {
        LexicalReranker reranker = reranker(true);
        List<RetrievalCandidate> candidates = List.of(
                candidate("A", "content", 0.03),
                candidate("B", "content", 0.04));
        assertThat(reranker.rerank("!!!", candidates)).extracting(RetrievalCandidate::identity)
                .containsExactly("A", "B");
    }
}
