package com.obsidianmind.parser;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wiki Link 解析器：提取 [[target]]、[[target|alias]]、[[target#heading]] 中的 target。
 */
@Component
public class WikiLinkParser {

    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^\\]]+)]]");

    /**
     * 提取正文中的 wiki link 目标。
     *
     * @param content Markdown 正文，null 或空白视为无链接
     * @return 去重后的 target 列表（保持出现顺序），已剥离 |alias 与 #heading 后缀
     */
    public java.util.List<String> parse(String content) {
        Set<String> targets = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return java.util.List.copyOf(targets);
        }
        Matcher matcher = WIKI_LINK.matcher(content);
        while (matcher.find()) {
            String raw = matcher.group(1);
            String target = raw.split("\\|")[0].split("#")[0].trim();
            if (!target.isEmpty()) {
                targets.add(target);
            }
        }
        return java.util.List.copyOf(targets);
    }
}
