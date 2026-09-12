package com.obsidianmind.controller;

import com.obsidianmind.dto.system.SystemStatusResponse;
import com.obsidianmind.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * System API：应用与依赖（Ollama / Milvus / Vault）健康状态。
 * 依赖未启动只影响对应状态显示，不影响本接口与应用运行。
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "系统状态与健康检查")
public class SystemController {

    private final HealthService healthService;

    @Value("${spring.application.name:ObsidianMind}")
    private String applicationName;

    public SystemController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/status")
    @Operation(summary = "系统状态")
    public SystemStatusResponse status() {
        return new SystemStatusResponse(
                applicationName,
                "UP",
                new SystemStatusResponse.ComponentStatus(healthService.checkOllama()),
                new SystemStatusResponse.ComponentStatus(healthService.checkMilvus()),
                new SystemStatusResponse.ComponentStatus(healthService.checkVault()));
    }
}
