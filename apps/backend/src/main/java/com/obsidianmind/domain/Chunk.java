package com.obsidianmind.domain;

import java.util.Map;

/**
 * 未来 RAG 数据模型：文档切块。heading 记录切块所在标题链，便于溯源。
 */
public record Chunk(
        String id,
        String noteId,
        String content,
        int chunkIndex,
        String heading,
        Map<String, Object> metadata
) {
}
