package com.obsidianmind.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health API：最简存活探针（基础设施阶段）。
 * 各依赖（Ollama / Milvus / Vault）的细分状态见 SystemController 的 /api/v1/system/status。
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
