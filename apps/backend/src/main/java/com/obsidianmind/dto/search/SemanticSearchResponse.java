package com.obsidianmind.dto.search;

import com.obsidianmind.service.RetrievalService;

import java.util.List;

/**
 * 语义检索响应：sources 面向用户展示（title/path/heading/snippet/score），
 * documentId/chunkIndex 保留用于前端定位笔记与未来 Citation。无匹配时 sources 为空列表（200）。
 */
public record SemanticSearchResponse(
        String query,
        List<RetrievalService.Source> sources,
        long elapsedMs) {
}
