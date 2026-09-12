package com.obsidianmind.domain;

import java.util.Map;

/**
 * 未来 RAG 数据模型：Note → Document → Chunk → Embedding。
 * 本阶段只建立清晰模型，不实现索引逻辑。
 */
public record Document(
        String id,
        String noteId,
        String title,
        String path,
        Map<String, Object> metadata
) {
}
