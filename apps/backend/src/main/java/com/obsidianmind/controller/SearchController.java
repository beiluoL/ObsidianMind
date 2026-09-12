package com.obsidianmind.controller;

import com.obsidianmind.dto.search.SearchResponse;
import com.obsidianmind.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Search API：全文搜索（标题 / 正文 / Tags / 路径）。
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "全文搜索（TEXT_MATCH 评分，非向量相似度）")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(summary = "全文搜索")
    public SearchResponse search(@RequestParam("q") String query) {
        return new SearchResponse(query, searchService.search(query));
    }
}
