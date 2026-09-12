package com.obsidianmind.parser;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MarkdownParser 单元测试：Frontmatter / Tags / WikiLink / 标题。
 */
class MarkdownParserTest {

    private final MarkdownParser parser = new MarkdownParser(new FrontmatterParser(), new WikiLinkParser());

    @Test
    void shouldParseFrontmatter() {
        String raw = """
                ---
                title: RAG 技术原理
                tags:
                  - AI
                  - RAG
                status: learning
                ---

                # RAG

                正文内容。
                """;
        ParsedMarkdown parsed = parser.parse(raw);
        assertEquals("RAG 技术原理", parsed.title());
        assertEquals(List.of("AI", "RAG"), parsed.tags());
        assertEquals("learning", ((Map<?, ?>) Map.copyOf(parsed.frontmatter())).get("status"));
        assertTrue(parsed.body().contains("# RAG"));
        assertTrue(!parsed.body().contains("title:"));
    }

    @Test
    void shouldParseInlineTagsAndExcludeHeadings() {
        String raw = """
                # 这是标题不是标签

                学习 #AI 和 #RAG 相关内容。

                ```java
                // #JavaTagInCode 不应计入
                ```
                """;
        ParsedMarkdown parsed = parser.parse(raw);
        assertEquals(List.of("AI", "RAG"), parsed.tags());
        assertNull(parsed.frontmatter().get("title"));
        assertEquals("这是标题不是标签", parsed.title());
    }

    @Test
    void shouldParseWikiLinksWithAliasAndHeading() {
        String raw = """
                关联 [[Embedding]]、[[Milvus|向量库]]、[[RAG#架构]]。
                """;
        List<String> links = parser.parse(raw).wikiLinks();
        assertEquals(List.of("Embedding", "Milvus", "RAG"), links);
    }

    @Test
    void shouldFallbackTitleToFirstHeading() {
        String raw = "# 我的标题\n\n内容";
        ParsedMarkdown parsed = parser.parse(raw);
        assertEquals("我的标题", parsed.title());
    }

    @Test
    void shouldHandleRawWithoutFrontmatter() {
        ParsedMarkdown parsed = parser.parse("# 纯文本\n无 frontmatter");
        assertTrue(parsed.frontmatter().isEmpty());
        assertTrue(parsed.tags().isEmpty());
        assertEquals("纯文本", parsed.title());
    }
}
