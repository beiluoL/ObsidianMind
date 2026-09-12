package com.obsidianmind.controller;

import com.obsidianmind.service.IndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Index API：触发索引任务（异步）与查询索引状态。
 * Phase 1 不强制连接 Milvus，只完成 解析 → Chunk 生成。
 */
@RestController
@RequestMapping("/api/v1/index")
@Tag(name = "Index", description = "索引任务与状态")
public class IndexController {

    private final IndexService indexService;

    public IndexController(IndexService indexService) {
        this.indexService = indexService;
    }

    @PostMapping
    @Operation(summary = "触发索引（异步：扫描 → 解析 → Chunk）")
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
