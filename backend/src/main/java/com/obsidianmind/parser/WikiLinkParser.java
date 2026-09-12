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
