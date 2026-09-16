package com.obsidianmind.controller;

import com.obsidianmind.dto.modelcenter.ModelCenterDtos.AvailableModelsResponse;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.ModelResponse;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.ModelUpsertRequest;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.ProviderResponse;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.ProviderUpsertRequest;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.TestConnectionRequest;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.TestConnectionResponse;
import com.obsidianmind.service.ModelCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Model Center REST API（Phase 5.5）：/api/v1/ai/**。
 * 薄壳——校验交给 DTO 注解，逻辑在 ModelCenterService。
 * 安全契约：任何端点都不返回 API Key 原文；请求中 apiKey 仅在 Upsert 时传入一次。
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Model Center", description = "AI Provider / Model 配置管理")
public class ModelCenterController {

    private final ModelCenterService service;

    public ModelCenterController(ModelCenterService service) {
        this.service = service;
    }

    // ---- Providers ----

    @GetMapping("/providers")
    @Operation(summary = "Provider 列表")
    public List<ProviderResponse> listProviders() {
        return service.listProviders();
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增 Provider")
    public ProviderResponse createProvider(@Valid @RequestBody ProviderUpsertRequest request) {
        return service.createProvider(request);
    }

    @PutMapping("/providers/{id}")
    @Operation(summary = "更新 Provider（apiKey 留空 = 保留原 Key）")
    public ProviderResponse updateProvider(@PathVariable String id,
                                           @Valid @RequestBody ProviderUpsertRequest request) {
        return service.updateProvider(id, request);
    }

    @DeleteMapping("/providers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除 Provider（级联删除其模型与凭据）")
    public void deleteProvider(@PathVariable String id) {
        service.deleteProvider(id);
    }

    @PostMapping("/providers/{id}/test")
    @Operation(summary = "测试连接（返回安全结果：success / latency / 语义化错误码）")
    public TestConnectionResponse testProvider(@PathVariable String id,
                                               @RequestBody(required = false) TestConnectionRequest request) {
        return service.testProvider(id, request);
    }

    @GetMapping("/providers/{id}/models")
    @Operation(summary = "Provider 下真实可用模型（当前仅 Ollama /api/tags）")
    public AvailableModelsResponse availableModels(@PathVariable String id) {
        return service.availableModels(id);
    }

    // ---- Models ----

    @GetMapping("/models")
    @Operation(summary = "Model 列表")
    public List<ModelResponse> listModels() {
        return service.listModels();
    }

    @PostMapping("/models")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增 Model")
    public ModelResponse createModel(@Valid @RequestBody ModelUpsertRequest request) {
        return service.createModel(request);
    }

    @PutMapping("/models/{id}")
    @Operation(summary = "更新 Model")
    public ModelResponse updateModel(@PathVariable String id, @Valid @RequestBody ModelUpsertRequest request) {
        return service.updateModel(id, request);
    }

    @DeleteMapping("/models/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除 Model")
    public void deleteModel(@PathVariable String id) {
        service.deleteModel(id);
    }

    @PostMapping("/models/{id}/default")
    @Operation(summary = "设为默认对话模型")
    public ModelResponse setDefaultModel(@PathVariable String id) {
        return service.setDefault(id);
    }
}
