package com.obsidianmind.retrieval;

import com.obsidianmind.config.RetrievalProperties;
import com.obsidianmind.domain.Chunk;
import com.obsidianmind.domain.Document;
import com.obsidianmind.parser.DocumentFactory;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.repository.FileMeta;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.service.Chunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Vault 关键词索引（Phase 6 MVP）：当前 Vault 的 Chunk 级内存倒排索引 + Okapi BM25。
 *
 * 为什么不用 Elasticsearch / Lucene / FTS：项目无数据库、无既有全文检索引擎，
 * 个人 Vault 规模（万级 Chunk 以下）内存 BM25 毫秒级完成（见评估报告耗时数据）。
 * 本类是实现细节，上层只依赖 {@link KeywordRetrievalProvider} → {@link RetrievalProvider}，
 * 未来换 Lucene / SQLite FTS5 时只替换实现，管线不动。
 *
 * 索引语义（与向量链路严格对齐，保证融合按 chunkId 成立）：
 * - 同一 MarkdownParser + Chunker 切块（DocumentFactory 共享），chunkId 形如 "{documentId}#c{n}"；
 * - 索引文本 = title + headingPath + content（标题/标题路径天然带权——重复出现在多字段等于提高词频）；
 * - 失效策略：每次检索前对比扫描元数据指纹（path→size+mtime），变化即全量重建（扫描只读元数据，廉价）。
 *
 * 中文支持：bigram 分词（TextTokenizer）——真实可用，不是假装；局限（无词典、未做同义）
 * 如实记录在 docs/architecture/hybrid-retrieval.md「Keyword Search Limited」一节。
 */
@Component
public class VaultKeywordIndex {

    private static final Logger log = LoggerFactory.getLogger(VaultKeywordIndex.class);

    private final VaultRepository vaultRepository;
    private final MarkdownParser markdownParser;
    private final Chunker chunker;
    private final RetrievalProperties retrievalProperties;

    private volatile IndexedCorpus corpus;

    public VaultKeywordIndex(VaultRepository vaultRepository,
                             MarkdownParser markdownParser,
                             Chunker chunker,
                             RetrievalProperties retrievalProperties) {
        this.vaultRepository = vaultRepository;
        this.markdownParser = markdownParser;
        this.chunker = chunker;
        this.retrievalProperties = retrievalProperties;
    }

    /**
     * BM25 检索：返回降序候选（最多 topK 条）。
     *
     * @throws com.obsidianmind.exception.VaultNotFoundException 未连接 Vault
     */
    public List<RetrievalCandidate> search(String query, int topK) {
        IndexedCorpus current = ensureFreshCorpus();
        return current.search(query, topK);
    }

    /** 当前索引的 Chunk 数（可观测：debug trace 与健康检查）。 */
    public int size() {
        IndexedCorpus current = corpus;
        return current == null ? 0 : current.documents.size();
    }

    /**
     * 惰性建索引 + 指纹失效。每次检索前先做一次元数据扫描（scan 只读元数据，个人 Vault 毫秒级），
     * 再对比指纹——文件增删改都能被探测到并触发重建，无需额外失效钩子。
     */
    private synchronized IndexedCorpus ensureFreshCorpus() {
        String vaultId = vaultRepository.requireVaultId();
        vaultRepository.scan();
        String fingerprint = fingerprint();
        IndexedCorpus current = corpus;
        if (current != null && current.vaultId().equals(vaultId) && current.fingerprint().equals(fingerprint)) {
            return current;
        }
        IndexedCorpus rebuilt = build(vaultId, fingerprint);
        corpus = rebuilt;
        log.info("关键词索引重建: chunks={} fingerprint={} ", rebuilt.documents.size(), fingerprint.length());
        return rebuilt;
    }

    /** 指纹 = 全部文件元数据的 path+size+mtime 拼接哈希视角（不做内容哈希——内容变化必然引起 mtime 变化）。 */
    private String fingerprint() {
        StringBuilder sb = new StringBuilder(256);
        for (FileMeta meta : vaultRepository.allNotes()) {
            sb.append(meta.path()).append(':').append(meta.size()).append(':')
                    .append(meta.modifiedAt() == null ? 0 : meta.modifiedAt().toEpochMilli()).append(';');
        }
        return sb.toString();
    }

    private IndexedCorpus build(String vaultId, String fingerprint) {
        List<Chunk> chunks = new ArrayList<>();
        for (FileMeta meta : vaultRepository.allNotes()) {
            try {
                String raw = vaultRepository.readRaw(meta.path());
                var parsed = markdownParser.parse(raw);
                String hash = com.obsidianmind.util.Hashes.sha256Hex(raw);
                Document document = DocumentFactory.create(vaultId, meta, parsed, hash);
                chunks.addAll(chunker.chunk(document));
            } catch (RuntimeException e) {
                // 单文件失败仅跳过（与 SearchService 同策略），不让一篇坏笔记杀死整条检索链
                log.warn("关键词索引跳过不可解析笔记: {}", meta.path());
            }
        }
        return new IndexedCorpus(vaultId, fingerprint, chunks,
                retrievalProperties.keywordOrDefault().k1(),
                retrievalProperties.keywordOrDefault().b());
    }

    /**
     * 不可变索引 + BM25 打分器。df / 平均长度在构建期算好，查询期只做累加。
     */
    private static final class IndexedCorpus {

        private final String vaultId;
        private final String fingerprint;
        private final List<IndexedChunk> documents;
        private final Map<String, Integer> documentFrequency;
        private final double avgDocLength;
        private final double k1;
        private final double b;

        IndexedCorpus(String vaultId, String fingerprint, List<Chunk> chunks, double k1, double b) {
            this.vaultId = vaultId;
            this.fingerprint = fingerprint;
            this.k1 = k1;
            this.b = b;
            this.documents = new ArrayList<>(chunks.size());
            Map<String, Integer> df = new HashMap<>();
            long totalLength = 0;
            for (Chunk chunk : chunks) {
                IndexedChunk indexed = new IndexedChunk(chunk);
                this.documents.add(indexed);
                totalLength += indexed.length();
                for (String term : indexed.termFrequencies().keySet()) {
                    df.merge(term, 1, Integer::sum);
                }
            }
            this.avgDocLength = documents.isEmpty() ? 1 : (double) totalLength / documents.size();
            this.documentFrequency = Map.copyOf(df);
        }

        String vaultId() {
            return vaultId;
        }

        String fingerprint() {
            return fingerprint;
        }

        List<RetrievalCandidate> search(String query, int topK) {
            if (documents.isEmpty() || query == null || query.isBlank() || topK <= 0) {
                return List.of();
            }
            Map<String, Integer> queryTerms = termFrequencies(TextTokenizer.tokenize(query));
            if (queryTerms.isEmpty()) {
                return List.of();
            }
            double n = documents.size();
            List<Scored> scored = new ArrayList<>(documents.size());
            for (IndexedChunk doc : documents) {
                double score = 0;
                boolean matched = false;
                for (Map.Entry<String, Integer> term : queryTerms.entrySet()) {
                    Integer df = documentFrequency.get(term.getKey());
                    if (df == null) {
                        continue;
                    }
                    Integer tf = doc.termFrequencies().get(term.getKey());
                    if (tf == null) {
                        continue;
                    }
                    matched = true;
                    double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                    double tfNorm = tf * (k1 + 1)
                            / (tf + k1 * (1 - b + b * doc.length() / avgDocLength));
                    score += idf * tfNorm;
                }
                if (matched && score > 0) {
                    scored.add(new Scored(doc.candidate(), score));
                }
            }
            scored.sort((x, y) -> Double.compare(y.score(), x.score()));
            return scored.stream()
                    .limit(topK)
                    .map(s -> new RetrievalCandidate(
                            s.candidate().chunkId(), s.candidate().documentId(), s.candidate().path(),
                            s.candidate().title(), s.candidate().heading(), s.candidate().chunkIndex(),
                            s.candidate().content(), Double.NaN, s.score(), Double.NaN, Double.NaN,
                            RetrievalCandidate.RetrievalType.KEYWORD))
                    .toList();
        }

        private static Map<String, Integer> termFrequencies(List<String> tokens) {
            Map<String, Integer> tf = new HashMap<>();
            for (String token : tokens) {
                tf.merge(token, 1, Integer::sum);
            }
            return tf;
        }

        private record Scored(RetrievalCandidate candidate, double score) {
        }
    }

    /** 索引单元：Chunk + 预分词词频（title/headingPath/content 合并索引）。 */
    private static final class IndexedChunk {

        private final RetrievalCandidate candidate;
        private final Map<String, Integer> termFrequencies;
        private final int length;

        IndexedChunk(Chunk chunk) {
            String indexText = String.join("\n",
                    Objects.requireNonNullElse(chunk.title(), ""),
                    Objects.requireNonNullElse(chunk.headingPath(), ""),
                    Objects.requireNonNullElse(chunk.content(), ""));
            List<String> tokens = TextTokenizer.tokenize(indexText);
            Map<String, Integer> tf = new HashMap<>();
            for (String token : tokens) {
                tf.merge(token, 1, Integer::sum);
            }
            this.termFrequencies = tf;
            this.length = Math.max(1, tokens.size());
            this.candidate = new RetrievalCandidate(
                    chunk.id(), chunk.documentId(), chunk.relativePath(), chunk.title(),
                    chunk.headingPath(), chunk.chunkIndex(), chunk.content(),
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    RetrievalCandidate.RetrievalType.KEYWORD);
        }

        Map<String, Integer> termFrequencies() {
            return termFrequencies;
        }

        int length() {
            return length;
        }

        RetrievalCandidate candidate() {
            return candidate;
        }
    }
}
