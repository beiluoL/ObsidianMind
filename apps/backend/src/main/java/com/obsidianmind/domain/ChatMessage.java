package com.obsidianmind.domain;

import java.time.Instant;
import java.util.List;

/**
 * 一条聊天消息。sources / relatedNotes 为未来 RAG 预留结构，Phase 1 可为空列表。
 */
public record ChatMessage(
        String id,
        String role,
        String content,
        List<Source> sources,
        List<Source> relatedNotes,
        Instant createdAt
) {
}
