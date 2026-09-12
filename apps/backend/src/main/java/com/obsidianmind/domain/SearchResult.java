package com.obsidianmind.domain;

/**
 * 全文搜索结果条目。
 * score 为文本匹配分（标题/Tag/正文词频加权），非向量相似度——Phase 2 引入 Milvus 后统一 SearchResult 语义。
 */
public record SearchResult(
        String noteId,
        String title,
        String path,
        String snippet,
        double score,
        String matchType
) {

    public static final String MATCH_TYPE_TEXT = "TEXT_MATCH";
}
