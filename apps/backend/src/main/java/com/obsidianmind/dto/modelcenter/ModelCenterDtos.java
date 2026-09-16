package com.obsidianmind.dto.modelcenter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Model Center REST DTO（Phase 5.5）。
 * 安全铁律：任何 Response 都不含 API Key 原文——只有 apiKeyConfigured / apiKeyMasked / credentialSource。
 * Upsert 请求中 apiKey 为 null 或空白 = 「不修改凭据」（编辑 Base URL 时保留原 Key），显式要删除走 DELETE 端点。
 */
public final class ModelCenterDtos {

    private ModelCenterDtos() {
    }

    // ---- Requests ----

    public record ProviderUpsertRequest(
            @NotBlank(message = "name 不能为空") @Size(max = 64, message = "name 过长") String name,
            @jakarta.validation.constraints.NotNull(message = "type 不能为空") AIProviderType type,
            @NotBlank(message = "baseUrl 不能为空") @Size(max = 512, message = "baseUrl 过长") String baseUrl,
            Boolean enabled,
            @Size(max = 4096, message = "apiKey 过长") String apiKey) {
    }

    /** 类型字面量（避免 DTO 直接暴露枚举导致 Jackson 反序列化错误信息暴露内部结构）。 */
    public enum AIProviderType {
        OLLAMA, DEEPSEEK, DASHSCOPE, OPENAI_COMPATIBLE
    }

    public record ModelUpsertRequest(
            @NotBlank(message = "providerId 不能为空") String providerId,
            @NotBlank(message = "modelName 不能为空") @Size(max = 128, message = "modelName 过长") String modelName,
            @Size(max = 128, message = "displayName 过长") String displayName,
            List<@Size(max = 32) String> capabilities,
            Boolean enabled,
            Boolean isDefault) {
    }

    public record TestConnectionRequest(@Size(max = 128) String modelName) {
    }

    // ---- Responses ----

    public record ProviderResponse(
            String id,
            String name,
            String type,
            String baseUrl,
            boolean enabled,
            boolean apiKeyConfigured,
            String apiKeyMasked,
            String credentialSource,
            String createdAt,
            String updatedAt) {
    }

    public record ModelResponse(
            String id,
            String providerId,
            String providerName,
            String modelName,
            String displayName,
            List<String> capabilities,
            boolean enabled,
            boolean isDefault,
            String createdAt,
            String updatedAt) {
    }

    /** 测试连接结果：safeMessage 已消毒（不含 Key / Authorization / 上游错误体）。 */
    public record TestConnectionResponse(
            boolean success,
            String provider,
            String model,
            long latencyMs,
            String errorCode,
            String message) {

        public static TestConnectionResponse ok(String provider, String model, long latencyMs) {
            return new TestConnectionResponse(true, provider, model, latencyMs, null, "连接成功");
        }

        public static TestConnectionResponse fail(String provider, String model, String errorCode, String message) {
            return new TestConnectionResponse(false, provider, model, -1, errorCode, message);
        }
    }

    /** Provider 下真实可用模型列表（当前仅 Ollama 支持，来自 /api/tags）。 */
    public record AvailableModelsResponse(String providerId, List<String> models) {
    }
}
