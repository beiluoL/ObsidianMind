package com.obsidianmind.util;

/**
 * 搜索摘要构建：围绕首个命中位置截取上下文窗口。
 */
public final class SnippetBuilder {

    private static final int CONTEXT_CHARS = 60;
    private static final int MAX_SNIPPET = 180;

    private SnippetBuilder() {
    }

    public static String build(String content, String query, int maxLen) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String lowerContent = content.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int hit = -1;
        for (String term : lowerQuery.split("\\s+")) {
            if (term.isBlank()) {
                continue;
            }
            int idx = lowerContent.indexOf(term);
            if (idx >= 0 && (hit < 0 || idx < hit)) {
                hit = idx;
            }
        }
        if (hit < 0) {
            String head = content.stripLeading();
            return head.length() <= maxLen ? head : head.substring(0, maxLen) + "…";
        }
        int from = Math.max(0, hit - CONTEXT_CHARS);
        int to = Math.min(content.length(), hit + maxLen);
        String snippet = content.substring(from, to).replaceAll("\n+", " ").trim();
        if (from > 0) {
            snippet = "…" + snippet;
        }
        if (to < content.length()) {
            snippet = snippet + "…";
        }
        return snippet.length() > MAX_SNIPPET ? snippet.substring(0, MAX_SNIPPET) : snippet;
    }
}
