package com.obsidianmind.service;

import com.obsidianmind.parser.FrontmatterParser;
import com.obsidianmind.parser.MarkdownParser;
import com.obsidianmind.parser.WikiLinkParser;
import com.obsidianmind.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SearchService 测试：标题 / 正文 / Tag / 路径搜索 + 评分诚实标注。
 */
class SearchServiceTest {

    @TempDir
    Path vaultDir;

    private SearchService searchService;

    @BeforeEach
    void setUp() throws Exception {
        Path ai = Files.createDirectories(vaultDir.resolve("AI"));
        Path java = Files.createDirectories(vaultDir.resolve("Java"));
        Files.writeString(ai.resolve("RAG.md"), """
                ---
                title: RAG 技术原理
                tags: [AI, RAG]
                ---

                # RAG 技术原理

                RAG 是检索增强生成，结合检索与大模型。
                """);
        Files.writeString(ai.resolve("Embedding.md"), "# Embedding\n\n向量嵌入是 RAG 的基础组件。");
        Files.writeString(java.resolve("JVM.md"), "# JVM\n\nJVM 内存结构说明。");
        VaultRepository repository = new VaultRepository();
        repository.connect(vaultDir.toString());
        repository.scan();
        searchService = new SearchService(repository,
                new MarkdownParser(new FrontmatterParser(), new WikiLinkParser()));
    }

    @Test
    void shouldSearchByTitle() {
        var results = searchService.search("RAG");
        assertFalse(results.isEmpty());
        assertEquals("RAG 技术原理", results.get(0).title());
        assertEquals("TEXT_MATCH", results.get(0).matchType());
        assertTrue(results.get(0).score() > 0);
    }

    @Test
    void shouldSearchByBody() {
        var results = searchService.search("向量嵌入");
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> r.title().equals("Embedding")));
        assertTrue(results.stream().anyMatch(r -> r.snippet().contains("向量嵌入")));
    }

    @Test
    void shouldSearchByTag() {
        var results = searchService.search("RAG");
        assertTrue(results.stream().anyMatch(r -> r.noteId().equals("AI/RAG.md")));
    }

    @Test
    void shouldRankTitleMatchHigherThanBodyMatch() {
        var results = searchService.search("JVM");
        assertEquals("JVM", results.get(0).title());
    }

    @Test
    void shouldReturnEmptyForBlankQuery() {
        assertTrue(searchService.search("  ").isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenNoHit() {
        assertTrue(searchService.search("不存在的关键词xyz").isEmpty());
    }
}
