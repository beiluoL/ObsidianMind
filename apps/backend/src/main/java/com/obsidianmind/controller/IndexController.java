package com.obsidianmind.controller;

import com.obsidianmind.service.IndexService;
import com.obsidianmind.service.KnowledgeIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Index API：知识索引（Phase 3 同步管线）+ Phase 1 兼容的异步任务与状态查询。
 * 同步端点直接返回各状态计数（indexed/updated/skipped/deleted/failed）与 per-file 错误明细。
 */
@RestController
@RequestMapping("/api/v1/index")
@Tag(name = "Index", description = "知识库索引（Markdown → Chunk → Embedding → Milvus）")
public class IndexController {

    private final IndexService indexService;
    private final KnowledgeIndexService knowledgeIndexService;

    public IndexController(IndexService indexService, KnowledgeIndexService knowledgeIndexService) {
        this.indexService = indexService;
        this.knowledgeIndexService = knowledgeIndexService;
    }

    @PostMapping("/run")
    @Operation(summary = "同步执行一次增量索引（扫描 → 解析 → 切块 → Embedding → Milvus，返回各状态计数）")
    public KnowledgeIndexService.IndexResult run() {
        return knowledgeIndexService.run();
    }

    @PostMapping
    @Operation(summary = "触发索引（Phase 1 兼容：异步执行，用 GET /status 轮询）")
    public Map<String, Object> startIndex() {
        indexService.startIndexing();
        return Map.of("accepted", true, "status", indexService.status().status());
    }

    @GetMapping("/status")
    @Operation(summary = "索引状态（IDLE/SCANNING/INDEXING/READY/FAILED）")
    public IndexService.IndexStatusResponse status() {
        return indexService.status();
    }
}
