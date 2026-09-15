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

    @Test
    void shouldHandleEmptyFile() {
        ParsedMarkdown parsed = parser.parse("");
        assertTrue(parsed.frontmatter().isEmpty());
        assertTrue(parsed.tags().isEmpty());
        assertTrue(parsed.wikiLinks().isEmpty());
        assertNull(parsed.title());
        assertEquals("", parsed.body());
    }

    @Test
    void shouldHandleOversizedFile() {
        // 约 2MB 的重复正文：解析必须可完成且不抛异常
        String paragraph = "这是性能测试段落，包含 #perf 标签与 [[Embedding]] 链接。\n\n";
        String raw = "# 大文件\n" + paragraph.repeat(40_000);
        ParsedMarkdown parsed = parser.parse(raw);
        assertEquals("大文件", parsed.title());
        assertTrue(parsed.tags().contains("perf"));
        assertEquals(List.of("Embedding"), parsed.wikiLinks());
    }

    @Test
    void shouldTolerateMalformedFrontmatter() {
        // 未闭合的 --- 分隔符：按无 frontmatter 处理，正文保持原文，不抛异常
        String unclosed = "---\ntitle: 未闭合\n\n# 正文\n内容";
        ParsedMarkdown parsed = parser.parse(unclosed);
        assertTrue(parsed.frontmatter().isEmpty());
        assertTrue(parsed.body().contains("title: 未闭合"));

        // YAML 语法非法：同样按无 frontmatter 处理
        String invalidYaml = "---\ntitle: [未闭合的列表\n---\n\n正文";
        ParsedMarkdown invalid = parser.parse(invalidYaml);
        assertTrue(invalid.frontmatter().isEmpty());
        assertEquals("正文", invalid.body().trim());
    }
}
