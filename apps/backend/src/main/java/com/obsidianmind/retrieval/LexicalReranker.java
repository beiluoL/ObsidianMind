package com.obsidianmind.retrieval;

import com.obsidianmind.config.RetrievalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 确定性词法 Reranker（Phase 6 第一版，非神经模型——诚实声明）。
 *
 * 原理：查询 token 的 IDF 加权覆盖率。
 *   rerankScore = Σ(命中查询词的 IDF) / Σ(全部查询词的 IDF)，无命中的候选为 0。
 * 用 IDF 而非裸词频：常用 bigram（如「一个」）几乎每篇都有，必须压低其权重，
 * 让 HashMap / NullPointerException 这类稀有且高信息量的 token 主导排序。
 *
 * 能力边界（不夸大）：
 * - 纯词法统计，不懂语义——同义改写无法提升；它的价值是把 RRF 里"词面高度吻合"的
 *   精确候选顶到前面（如「ConcurrentHashMap CAS」场景）；
 * - 完全确定性：同输入永远同输出，回归测试可复现；
 * - 真正的神经 Reranker（bge-reranker 等）NOT_CONFIGURED——评估报告如实标注。
 */
@Component
public class LexicalReranker implements RerankerProvider {

    private static final Logger log = LoggerFactory.getLogger(LexicalReranker.class);

    private final RetrievalProperties properties;

    public LexicalReranker(RetrievalProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "lexical";
    }

    @Override
    public boolean isAvailable() {
        return properties.rerankerOrDefault().enabled();
    }

    @Override
    public List<RetrievalCandidate> rerank(String query, List<RetrievalCandidate> candidates) {
        if (!isAvailable()) {
            throw new IllegalStateException("Reranker 未启用（NOT_CONFIGURED），不应被调用");
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<String> queryTokens = TextTokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            // 查询无有效 token：保持原序，rerankScore 不写入（NaN = 未评分）
            log.info("Reranker 跳过：查询无有效 token");
            return List.copyOf(candidates);
        }

        // 查询词的文档频率：在同一候选集内估计（候选集即当前评估的微型语料）
        Map<String, Integer> documentFrequency = new HashMap<>();
        List<Set<String>> candidateTokenSets = new ArrayList<>(candidates.size());
        for (RetrievalCandidate candidate : candidates) {
            List<String> tokens = TextTokenizer.tokenize(candidate.title() + "\n"
                    + candidate.heading() + "\n" + candidate.content());
            Set<String> unique = new HashSet<>(tokens);
            candidateTokenSets.add(unique);
            for (String token : unique) {
                documentFrequency.merge(token, 1, Integer::sum);
            }
        }
        int corpusSize = candidates.size();

        record ScoredCandidate(RetrievalCandidate candidate, double score) {
        }
        List<ScoredCandidate> scored = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            Set<String> tokens = candidateTokenSets.get(i);
            double hitIdf = 0;
            double totalIdf = 0;
            for (String token : new HashSet<>(queryTokens)) {
                int df = documentFrequency.getOrDefault(token, 0);
                double idf = Math.log(1 + (double) corpusSize / (df + 1));
                totalIdf += idf;
                if (tokens.contains(token)) {
                    hitIdf += idf;
                }
            }
            double score = totalIdf > 0 ? hitIdf / totalIdf : 0;
            RetrievalCandidate candidate = candidates.get(i);
            scored.add(new ScoredCandidate(
                    candidate.withScores(candidate.rrfScore(), score, candidate.retrievalType()),
                    score));
        }
        // rerankScore 降序；List.sort 稳定 → 同分保持 RRF 原序（确定性）
        scored.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());
        List<RetrievalCandidate> result = new ArrayList<>(candidates.size());
        for (ScoredCandidate item : scored) {
            result.add(item.candidate());
        }
        log.info("Reranker 完成: candidates={} topScore={}",
                candidates.size(), scored.isEmpty() ? 0 : scored.get(0).score());
        return List.copyOf(result);
    }
}
