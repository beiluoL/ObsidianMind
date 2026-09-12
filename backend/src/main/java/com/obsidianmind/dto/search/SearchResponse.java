package com.obsidianmind.dto.search;

import com.obsidianmind.domain.SearchResult;

import java.util.List;

/**
 * 搜索响应。
 */
public record SearchResponse(String query, List<SearchResult> results) {
}
