package com.obsidianmind.controller;

import com.obsidianmind.dto.search.SemanticSearchRequest;
import com.obsidianmind.dto.search.SemanticSearchResponse;
import com.obsidianmind.dto.search.SearchResponse;
import com.obsidianmind.service.RetrievalService;
import com.obsidianmind.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Search API：全文搜索（标题 / 正文 / Tags / 路径）+ 语义检索（Query → Embedding → Vector Search → Sources）。
 * Controller 只做 Request → Service → Response；校验、嵌入、过滤、排序、Source 映射全在 RetrievalService。
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "全文搜索（TEXT_MATCH 评分）与语义检索（COSINE 相似度）")
public class SearchController {

    private final SearchService searchService;
    private final RetrievalService retrievalService;

    public SearchController(SearchService searchService, RetrievalService retrievalService) {
        this.searchService = searchService;
        this.retrievalService = retrievalService;
    }

    @GetMapping
    @Operation(summary = "全文搜索")
    public SearchResponse search(@RequestParam("q") String query) {
        return new SearchResponse(query, searchService.search(query));
    }

    @PostMapping("/semantic")
    @Operation(summary = "语义检索（向量相似度，返回可定位的 Sources）")
    public SemanticSearchResponse semanticSearch(@RequestBody SemanticSearchRequest request) {
        RetrievalService.RetrievalResult result = retrievalService.retrieve(request.query(), request.topK());
        return new SemanticSearchResponse(result.query(), result.results(), result.elapsedMs());
    }
}
