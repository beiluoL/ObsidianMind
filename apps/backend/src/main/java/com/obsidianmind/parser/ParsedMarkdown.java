package com.obsidianmind.parser;

import java.util.List;
import java.util.Map;

/**
 * Markdown 解析结果：frontmatter + tags + wiki links + 标题 + 正文。
 */
public record ParsedMarkdown(
        Map<String, Object> frontmatter,
        List<String> tags,
        List<String> wikiLinks,
        String title,
        String body
) {
}
