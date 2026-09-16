package com.obsidianmind.controller;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.RetrievalProperties;
import com.obsidianmind.dto.search.HybridSearchRequest;
import com.obsidianmind.dto.search.HybridSearchResponse;
import com.obsidianmind.dto.search.RetrievalConfigResponse;
import com.obsidianmind.dto.search.RetrievalDebugResponse;
import com.obsidianmind.dto.search.SearchResponse;
import com.obsidianmind.dto.search.SemanticSearchRequest;
import com.obsidianmind.dto.search.SemanticSearchResponse;
import com.obsidianmind.exception.InvalidRequestException;
import com.obsidianmind.retrieval.RetrievalMode;
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
 * Search API：全文搜索（TEXT_MATCH）+ 语义检索（Phase 4）+ 混合检索（Phase 6 Hybrid）。
 * Controller 只做 Request → Service → Response；校验、编排、降级、Source 映射全在 Service 层。
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "全文搜索（TEXT_MATCH 评分）、语义检索（COSINE 相似度）与混合检索（BM25+向量+RRF+Reranker）")
public class SearchController {

    private final SearchService searchService;
    private final RetrievalService retrievalService;
    private final AiProperties aiProperties;
    private final RetrievalProperties retrievalProperties;

    public SearchController(SearchService searchService,
                            RetrievalService retrievalService,
                            AiProperties aiProperties,
                            RetrievalProperties retrievalProperties) {
        this.searchService = searchService;
        this.retrievalService = retrievalService;
        this.aiProperties = aiProperties;
        this.retrievalProperties = retrievalProperties;
    }

    @GetMapping
    @Operation(summary = "全文搜索")
    public SearchResponse search(@RequestParam("q") String query) {
        return new SearchResponse(query, searchService.search(query));
    }

    @PostMapping
    @Operation(summary = "混合检索（VECTOR / KEYWORD / HYBRID，默认 HYBRID；失败自动降级且可观察）")
    public HybridSearchResponse hybridSearch(@RequestBody HybridSearchRequest request) {
        RetrievalMode mode = RetrievalMode.parse(request.modeOrDefault(),
                retrievalProperties.defaultModeOrDefault());
        RetrievalService.HybridRetrievalResult result =
                retrievalService.retrieveHybrid(request.query(), mode, request.topK());
        return new HybridSearchResponse(result.query(), result.requestedMode(), result.effectiveMode(),
                result.fallbacks(), result.rerankerStatus(), result.elapsedMs(), result.results());
    }

    @PostMapping("/semantic")
    @Operation(summary = "语义检索（向量相似度，返回可定位的 Sources）；Phase 6 起默认模式由服务端配置决定")
    public SemanticSearchResponse semanticSearch(@RequestBody SemanticSearchRequest request) {
        RetrievalService.RetrievalResult result = retrievalService.retrieve(request.query(), request.topK());
        return new SemanticSearchResponse(result.query(), result.results(), result.elapsedMs());
    }

    @GetMapping("/config")
    @Operation(summary = "检索配置（只读，Advanced Retrieval / Debug 面板数据源）")
    public RetrievalConfigResponse config() {
        return RetrievalConfigResponse.of(aiProperties, retrievalProperties);
    }

    @PostMapping("/debug")
    @Operation(summary = "Retrieval Debug（仅 retrieval.debug-enabled=true 时可用；返回全链路 trace）")
    public RetrievalDebugResponse debug(@RequestBody HybridSearchRequest request) {
        requireDebugEnabled();
        RetrievalMode mode = RetrievalMode.parse(request.modeOrDefault(),
                retrievalProperties.defaultModeOrDefault());
        return RetrievalDebugResponse.of(retrievalService.retrieveWithTrace(request.query(), mode, request.topK()));
    }

    /** Debug 门禁：默认关闭（403），不为"看起来高级"在生产暴露内部 trace。 */
    private void requireDebugEnabled() {
        if (!retrievalProperties.debugEnabled()) {
            throw new InvalidRequestException("Retrieval Debug 未启用（设置 RETRIEVAL_DEBUG_ENABLED=true 开启）");
        }
    }
}
