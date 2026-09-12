package com.obsidianmind.dto.chat;

import com.obsidianmind.domain.SearchResult;

import java.util.List;

/**
 * 聊天响应：answer + sources + relatedNotes（未来 RAG 契约，Phase 1 sources 恒为空）。
 */
public record ChatResponse(String answer, List<SearchResult> sources, List<SearchResult> relatedNotes) {
}
