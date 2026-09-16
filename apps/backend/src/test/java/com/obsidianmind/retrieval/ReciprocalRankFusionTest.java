package com.obsidianmind.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RRF 融合测试：多结果集融合顺序、k 语义、分数通道不混淆、单路/空输入。
 */
class ReciprocalRankFusionTest {

    private static RetrievalCandidate candidate(String id, RetrievalCandidate.RetrievalType type, double rawScore) {
        double vectorScore = type == RetrievalCandidate.RetrievalType.VECTOR ? rawScore : Double.NaN;
        double keywordScore = type == RetrievalCandidate.RetrievalType.KEYWORD ? rawScore : Double.NaN;
        return new RetrievalCandidate(id, id, id + ".md", id, "H", 0, "content " + id,
                vectorScore, keywordScore, Double.NaN, Double.NaN, type);
    }

    @Test
    void fusionFollowsRrfFormulaAndProducesDocumentedOrder() {
        // 需求文档示例：Vector A>B>C，Keyword C>A>D → RRF 期望 A > C > B > D
        List<RetrievalCandidate> vector = List.of(
                candidate("A", RetrievalCandidate.RetrievalType.VECTOR, 0.9),
                candidate("B", RetrievalCandidate.RetrievalType.VECTOR, 0.8),
                candidate("C", RetrievalCandidate.RetrievalType.VECTOR, 0.7));
        List<RetrievalCandidate> keyword = List.of(
                candidate("C", RetrievalCandidate.RetrievalType.KEYWORD, 12.0),
                candidate("A", RetrievalCandidate.RetrievalType.KEYWORD, 10.0),
                candidate("D", RetrievalCandidate.RetrievalType.KEYWORD, 8.0));

        List<RetrievalCandidate> fused = ReciprocalRankFusion.fuse(List.of(vector, keyword), 60);

        assertThat(fused).extracting(RetrievalCandidate::identity)
                .containsExactly("A", "C", "B", "D");
        // 公式核验：A = 1/61 + 1/62，C = 1/63 + 1/61
        RetrievalCandidate a = fused.get(0);
        assertThat(a.rrfScore()).isCloseTo(1.0 / 61 + 1.0 / 62, org.assertj.core.data.Offset.offset(1e-12));
        RetrievalCandidate c = fused.get(1);
        assertThat(c.rrfScore()).isCloseTo(1.0 / 63 + 1.0 / 61, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void bothHitCandidatesAreMarkedHybridAndKeepRawScores() {
        List<RetrievalCandidate> vector = List.of(
                candidate("A", RetrievalCandidate.RetrievalType.VECTOR, 0.9));
        List<RetrievalCandidate> keyword = List.of(
                candidate("A", RetrievalCandidate.RetrievalType.KEYWORD, 7.5));

        List<RetrievalCandidate> fused = ReciprocalRankFusion.fuse(List.of(vector, keyword), 60);

        assertThat(fused).hasSize(1);
        RetrievalCandidate a = fused.get(0);
        assertThat(a.retrievalType()).isEqualTo(RetrievalCandidate.RetrievalType.HYBRID);
        assertThat(a.vectorScore()).isEqualTo(0.9); // 原始分不丢：debug 与评估依赖
        assertThat(a.keywordScore()).isEqualTo(7.5);
        assertThat(a.rrfScore()).isCloseTo(2.0 / 61, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void singleHitCandidatesKeepTheirOwnType() {
        List<RetrievalCandidate> vector = List.of(
                candidate("A", RetrievalCandidate.RetrievalType.VECTOR, 0.9));
        List<RetrievalCandidate> keyword = List.of(
                candidate("B", RetrievalCandidate.RetrievalType.KEYWORD, 7.5));

        List<RetrievalCandidate> fused = ReciprocalRankFusion.fuse(List.of(vector, keyword), 60);

        assertThat(fused).extracting(RetrievalCandidate::identity).containsExactly("A", "B");
        assertThat(fused.get(0).retrievalType()).isEqualTo(RetrievalCandidate.RetrievalType.VECTOR);
        assertThat(fused.get(1).retrievalType()).isEqualTo(RetrievalCandidate.RetrievalType.KEYWORD);
    }

    @Test
    void smallerKGivesHeadRanksMoreWeight() {
        List<RetrievalCandidate> vector = List.of(
                candidate("A", RetrievalCandidate.RetrievalType.VECTOR, 0.9),
                candidate("B", RetrievalCandidate.RetrievalType.VECTOR, 0.1));
        List<RetrievalCandidate> keyword = List.of(
                candidate("B", RetrievalCandidate.RetrievalType.KEYWORD, 9.0),
                candidate("A", RetrievalCandidate.RetrievalType.KEYWORD, 1.0));

        // k=1：头部差距被放大；B 在 keyword rank1 + vector rank2，应当反超 A（A 在两路都是 rank1/rank2 对称）
        // A = 1/2 + 1/61 ≈ 0.5164；B = 1/61 + 1/2 对称相同 → 用 k=1 时 B = 1/61+1/2 与 A 相同，改用非对称样例
        List<RetrievalCandidate> kw = List.of(
                candidate("A", RetrievalCandidate.RetrievalType.KEYWORD, 9.0),
                candidate("B", RetrievalCandidate.RetrievalType.KEYWORD, 1.0));
        List<RetrievalCandidate> fusedK1 = ReciprocalRankFusion.fuse(List.of(vector, kw), 1);
        List<RetrievalCandidate> fusedK1000 = ReciprocalRankFusion.fuse(List.of(vector, kw), 1000);

        // A 恒为 (1,1) 两路 rank1；B 为 (2,2)。k 越小，A 领先 B 的幅度越大
        double gapK1 = fusedK1.get(0).rrfScore() - fusedK1.get(1).rrfScore();
        double gapK1000 = fusedK1000.get(0).rrfScore() - fusedK1000.get(1).rrfScore();
        assertThat(gapK1).isGreaterThan(gapK1000);
    }

    @Test
    void emptyAndNullListsAreTolerated() {
        assertThat(ReciprocalRankFusion.fuse(List.of(), 60)).isEmpty();
        List<List<RetrievalCandidate>> withNull = new java.util.ArrayList<>();
        withNull.add(List.of());
        withNull.add(null);
        assertThat(ReciprocalRankFusion.fuse(withNull, 60)).isEmpty();
    }

    @Test
    void nonPositiveKIsRejected() {
        assertThatThrownBy(() -> ReciprocalRankFusion.fuse(List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
