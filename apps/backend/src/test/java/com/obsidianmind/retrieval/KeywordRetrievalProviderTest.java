package com.obsidianmind.retrieval;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.parser.FrontmatterParser;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.WikiLinkParser;
import com.obsidianmind.repository.VaultRepository;
import com.obsidianmind.service.Chunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 关键词检索 Provider 测试：精确关键词、中文、特殊字符（注入安全）、空输入、索引失效重建。
 * Vault 用 @TempDir + 真实 Parser/Chunker（与生产链路一致，保证 chunkId 可与向量路对齐）。
 */
class KeywordRetrievalProviderTest {

    @TempDir
    Path vaultDir;

    private VaultRepository vaultRepository;
    private KeywordRetrievalProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        vaultRepository = new VaultRepository();
        vaultRepository.connect(vaultDir.toString());
        AiProperties ai = new AiProperties("ollama", null,
                new AiProperties.Chunk(800, 100), null, null, null);
        VaultKeywordIndex index = new VaultKeywordIndex(vaultRepository,
                new MarkdownParser(new FrontmatterParser(), new WikiLinkParser()),
                new Chunker(ai), RetrievalStackForTest.properties("KEYWORD"));
        provider = new KeywordRetrievalProvider(vaultRepository, index);
    }

    private void note(String relativePath, String content) throws Exception {
        Path file = vaultDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void exactClassNameQueryRanksMatchingNoteFirst() throws Exception {
        note("Java/HashMap.md", "# HashMap\n\nHashMap 底层是数组 + 链表 + 红黑树。扩容在超过负载因子时触发。\n");
        note("AI/Embedding.md", "# Embedding\n\n向量嵌入把文本映射为高维向量，与 Java 无关。\n");

        List<RetrievalCandidate> results = provider.retrieve("HashMap", 5);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).documentId()).isEqualTo("Java/HashMap.md");
        assertThat(results.get(0).retrievalType()).isEqualTo(RetrievalCandidate.RetrievalType.KEYWORD);
        assertThat(results.get(0).keywordScore()).isPositive();
    }

    @Test
    void chineseNaturalLanguageQueryFindsSemantics() throws Exception {
        note("Java/HashMap.md", "# HashMap\n\n为什么 HashMap 需要扩容？因为负载因子超过 0.75 时冲突增多。\n");
        note("AI/Token.md", "# Token\n\nToken 是模型词表的最小单位。\n");

        List<RetrievalCandidate> results = provider.retrieve("HashMap 为什么需要扩容", 5);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).documentId()).isEqualTo("Java/HashMap.md");
    }

    @Test
    void querySyntaxCharactersAreInjectionSafe() throws Exception {
        note("Java/HashMap.md", "# HashMap\n\nHashMap 扩容与 resize 的触发条件。\n");
        note("Java/Safe.md", "# Safe\n\n普通内容，包含 OR 与 NOT 字样。\n");

        // Lucene 语法字符应被视为普通文本，不产生查询语法效果
        List<RetrievalCandidate> results = provider.retrieve(
                "HashMap OR NOT \"扩容\" +resize - (trigger) AND", 5);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).documentId()).isEqualTo("Java/HashMap.md");
    }

    @Test
    void emptyVaultReturnsEmptyResult() {
        assertThat(provider.retrieve("HashMap", 5)).isEmpty();
    }

    @Test
    void nonsenseQueryReturnsEmptyResult() throws Exception {
        note("Java/HashMap.md", "# HashMap\n\nHashMap 扩容。\n");
        assertThat(provider.retrieve("完全不存在的量子术语xyz", 5)).isEmpty();
    }

    @Test
    void indexRebuildsWhenFileChanges() throws Exception {
        note("Java/HashMap.md", "# HashMap\n\n旧内容：负载因子。\n");
        assertThat(provider.retrieve("黑科技词", 5)).isEmpty();

        note("Java/HashMap.md", "# HashMap\n\n新内容包含黑科技词。\n");
        // mtime 变化触发指纹失效 → 索引重建 → 新词可命中
        List<RetrievalCandidate> results = provider.retrieve("黑科技词", 5);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).documentId()).isEqualTo("Java/HashMap.md");
    }

    @Test
    void candidateCarriesCompleteSourceMetadata() throws Exception {
        note("Java/HashMap.md", "# HashMap\n\n负载因子超过 0.75 触发 resize 扩容。\n");

        List<RetrievalCandidate> results = provider.retrieve("负载因子", 5);

        assertThat(results).hasSize(1);
        RetrievalCandidate c = results.get(0);
        assertThat(c.chunkId()).isEqualTo("Java/HashMap.md#c0");
        assertThat(c.documentId()).isEqualTo("Java/HashMap.md");
        assertThat(c.path()).isEqualTo("Java/HashMap.md");
        assertThat(c.title()).isEqualTo("HashMap");
        assertThat(c.heading()).contains("HashMap");
        assertThat(c.chunkIndex()).isZero();
        assertThat(c.content()).contains("resize");
    }
}
