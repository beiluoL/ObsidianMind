package com.obsidianmind.parser;

import com.obsidianmind.parser.FrontmatterParser.Result;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 解析门面：frontmatter + tags（frontmatter 与正文行内 #tag 合并）+ wiki links + 首个一级标题。
 * 只读解析，不修改原始内容。
 */
@Component
public class MarkdownParser {

    private static final Pattern INLINE_TAG = Pattern.compile("(?<![\\w#/])#([\\p{L}\\p{N}][\\p{L}\\p{N}_/-]*)");
    private static final Pattern H1 = Pattern.compile("(?m)^#\\s+(.+)$");
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```.*?```");

    private final FrontmatterParser frontmatterParser;
    private final WikiLinkParser wikiLinkParser;

    public MarkdownParser(FrontmatterParser frontmatterParser, WikiLinkParser wikiLinkParser) {
        this.frontmatterParser = frontmatterParser;
        this.wikiLinkParser = wikiLinkParser;
    }

    public ParsedMarkdown parse(String raw) {
        Result fm = frontmatterParser.parse(raw);
        Map<String, Object> metadata = fm.metadata();
        String body = fm.body();

        Set<String> tags = new LinkedHashSet<>();
        collectFrontmatterTags(metadata.get("tags"), tags);
        collectInlineTags(body, tags);

        List<String> links = wikiLinkParser.parse(body);
        String title = resolveTitle(metadata, body);
        return new ParsedMarkdown(metadata, List.copyOf(tags), links, title, body);
    }

    /** 从 frontmatter title 字段或正文首个 "# 标题" 解析标题，无则返回 null（由调用方用文件名兜底）。 */
    private String resolveTitle(Map<String, Object> metadata, String body) {
        Object fmTitle = metadata.get("title");
        if (fmTitle instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        Matcher matcher = H1.matcher(stripCodeFences(body));
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private void collectFrontmatterTags(Object value, Set<String> tags) {
        if (value instanceof java.util.List<?> list) {
            for (Object item : list) {
                addTag(item, tags);
            }
        } else {
            addTag(value, tags);
        }
    }

    private void addTag(Object value, Set<String> tags) {
        if (value instanceof String s && !s.isBlank()) {
            tags.add(s.trim());
        }
    }

    private void collectInlineTags(String body, Set<String> tags) {
        String stripped = stripCodeFences(body);
        Matcher matcher = INLINE_TAG.matcher(stripped);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            String tag = matcher.group(1).trim();
            if (!tag.isEmpty()) {
                found.add(tag);
            }
        }
        tags.addAll(found);
    }

    private String stripCodeFences(String content) {
        return content == null ? "" : CODE_FENCE.matcher(content).replaceAll("");
    }
}
