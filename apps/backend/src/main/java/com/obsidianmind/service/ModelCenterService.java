package com.obsidianmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidianmind.config.AiProperties;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.AvailableModelsResponse;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.ModelResponse;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.ModelUpsertRequest;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.ProviderResponse;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.ProviderUpsertRequest;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.TestConnectionRequest;
import com.obsidianmind.dto.modelcenter.ModelCenterDtos.TestConnectionResponse;
import com.obsidianmind.exception.LlmUnavailableException;
import com.obsidianmind.exception.ModelCenterException;
import com.obsidianmind.modelcenter.AIModel;
import com.obsidianmind.modelcenter.AIProvider;
import com.obsidianmind.modelcenter.ChatModelAdapter;
import com.obsidianmind.modelcenter.ChatModelAdapter.ChatOptions;
import com.obsidianmind.modelcenter.CredentialResolver;
import com.obsidianmind.modelcenter.CredentialStore;
import com.obsidianmind.modelcenter.ModelCenterStorage;
import com.obsidianmind.modelcenter.ModelRouter;
import com.obsidianmind.modelcenter.OpenAiCompatibleChatModelAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Model Center 服务（Phase 5.5）：Provider / Model CRUD、默认模型、测试连接、Ollama 可用模型读取。
 * 只做编排与校验；持久化在 ModelCenterStorage，凭据在 CredentialStore，执行在 ChatModelAdapter。
 *
 * 写操作全部走 storage + router.invalidate()——用户换 Key 后旧客户端立即作废（缓存失效契约）。
 */
@Service
public class ModelCenterService {

    private static final Logger log = LoggerFactory.getLogger(ModelCenterService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_CAPABILITIES =
            Set.of(AIModel.CAP_CHAT, AIModel.CAP_STREAMING, AIModel.CAP_REASONING);

    private final ModelCenterStorage storage;
    private final CredentialResolver credentialResolver;
    private final ModelRouter router;
    private final AiProperties aiProperties;

    public ModelCenterService(ModelCenterStorage storage, CredentialResolver credentialResolver,
                              ModelRouter router, AiProperties aiProperties) {
        this.storage = storage;
        this.credentialResolver = credentialResolver;
        this.router = router;
        this.aiProperties = aiProperties;
    }

    // ---- Provider ----

    public List<ProviderResponse> listProviders() {
        return storage.listProviders().stream().map(this::toProviderResponse).toList();
    }

    public ProviderResponse createProvider(ProviderUpsertRequest req) {
        AIProvider provider = new AIProvider(
                UUID.randomUUID().toString(), req.name().strip(), AIProvider.Type.valueOf(req.type().name()),
                normalizeBaseUrl(req.baseUrl()), req.enabled() == null || req.enabled(),
                Instant.now(), Instant.now());
        storage.upsertProvider(provider);
        applyCredential(provider, req.apiKey());
        router.invalidate();
        log.info("Provider 已创建: id={} type={}", provider.getId(), provider.getType());
        return toProviderResponse(provider);
    }

    public ProviderResponse updateProvider(String id, ProviderUpsertRequest req) {
        AIProvider existing = requireProvider(id).copy();
        existing.setName(req.name().strip());
        existing.setType(AIProvider.Type.valueOf(req.type().name()));
        existing.setBaseUrl(normalizeBaseUrl(req.baseUrl()));
        existing.setEnabled(req.enabled() == null || req.enabled());
        existing.setUpdatedAt(Instant.now());
        storage.upsertProvider(existing);
        applyCredential(existing, req.apiKey()); // apiKey null/blank = 保留原凭据（编辑 baseUrl 不清 Key）
        router.invalidate();
        log.info("Provider 已更新: id={}", id);
        return toProviderResponse(existing);
    }

    public void deleteProvider(String id) {
        requireProvider(id);
        storage.deleteProvider(id);
        router.invalidate();
        log.info("Provider 已删除: id={}", id);
    }

    /** 测试连接：providerId + 可选 modelName（无模型条目时也能测）；结果只含安全信息。 */
    public TestConnectionResponse testProvider(String providerId, TestConnectionRequest req) {
        AIProvider provider = requireProvider(providerId);
        String modelName = req == null || req.modelName() == null || req.modelName().isBlank()
                ? null : req.modelName().strip();
        long startedAt = System.currentTimeMillis();
        try {
            if (provider.getType() == AIProvider.Type.OLLAMA) {
                return testOllama(provider, modelName, startedAt);
            }
            return testOpenAiCompatible(provider, modelName, startedAt);
        } catch (ModelCenterException e) {
            return TestConnectionResponse.fail(provider.getName(), modelName, e.getCode(), e.getMessage());
        } catch (LlmUnavailableException e) {
            // 适配器已消毒的消息（含语义化错误码），可直接返回
            return TestConnectionResponse.fail(provider.getName(), modelName, "PROVIDER_ERROR", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("测试连接未预期失败: {}", e.getClass().getSimpleName());
            return TestConnectionResponse.fail(provider.getName(), modelName, "PROVIDER_ERROR",
                    "连接失败：" + provider.getName() + " 服务不可达");
        }
    }

    /** Ollama 真实已安装模型（/api/tags）；其他类型无可靠 Model List 接口，明确不支持而非假装支持。 */
    public AvailableModelsResponse availableModels(String providerId) {
        AIProvider provider = requireProvider(providerId);
        if (provider.getType() != AIProvider.Type.OLLAMA) {
            throw new ModelCenterException("NOT_SUPPORTED", "该 Provider 不支持自动获取模型列表，请手动填写 Model ID");
        }
        try {
            String body = RestClient.create().get()
                    .uri(provider.getBaseUrl() + "/api/tags")
                    .retrieve().body(String.class);
            JsonNode root = MAPPER.readTree(body);
            List<String> models = root.path("models").valueStream()
                    .map(n -> n.path("name").asText(""))
                    .filter(s -> !s.isEmpty())
                    .sorted()
                    .toList();
            return new AvailableModelsResponse(providerId, models);
        } catch (Exception e) {
            log.warn("读取 Ollama 模型列表失败: {}", e.getMessage());
            throw new ModelCenterException("PROVIDER_UNAVAILABLE", "无法连接 Ollama（" + provider.getBaseUrl() + "）");
        }
    }

    // ---- Model ----

    public List<ModelResponse> listModels() {
        Map<String, AIProvider> providers = storage.listProviders().stream()
                .collect(Collectors.toMap(AIProvider::getId, Function.identity()));
        return storage.listModels().stream()
                .map(m -> toModelResponse(m, providers.get(m.getProviderId())))
                .toList();
    }

    public ModelResponse createModel(ModelUpsertRequest req) {
        AIProvider provider = requireProvider(req.providerId());
        validateCapabilities(req.capabilities());
        if (req.modelName() == null || req.modelName().isBlank()) {
            throw new ModelCenterException("INVALID_REQUEST", "modelName 不能为空");
        }
        AIModel model = new AIModel(
                UUID.randomUUID().toString(), provider.getId(), req.modelName().strip(),
                (req.displayName() == null || req.displayName().isBlank())
                        ? req.modelName().strip() : req.displayName().strip(),
                normalizeCapabilities(req.capabilities()),
                req.enabled() == null || req.enabled(),
                Boolean.TRUE.equals(req.isDefault()),
                Instant.now(), Instant.now());
        storage.upsertModel(model);
        router.invalidate();
        log.info("Model 已创建: id={} provider={} model={}", model.getId(), provider.getId(), model.getModelName());
        return toModelResponse(model, provider);
    }

    public ModelResponse updateModel(String id, ModelUpsertRequest req) {
        AIModel existing = requireModel(id).copy();
        AIProvider provider = requireProvider(req.providerId());
        validateCapabilities(req.capabilities());
        existing.setProviderId(provider.getId());
        existing.setModelName(req.modelName().strip());
        existing.setDisplayName((req.displayName() == null || req.displayName().isBlank())
                ? req.modelName().strip() : req.displayName().strip());
        existing.setCapabilities(normalizeCapabilities(req.capabilities()));
        existing.setEnabled(req.enabled() == null || req.enabled());
        existing.setDefault(Boolean.TRUE.equals(req.isDefault()));
        existing.setUpdatedAt(Instant.now());
        storage.upsertModel(existing);
        router.invalidate();
        log.info("Model 已更新: id={}", id);
        return toModelResponse(existing, provider);
    }

    public void deleteModel(String id) {
        requireModel(id);
        storage.deleteModel(id);
        router.invalidate();
        log.info("Model 已删除: id={}", id);
    }

    public ModelResponse setDefault(String id) {
        AIModel model = requireModel(id).copy();
        model.setDefault(true);
        model.setUpdatedAt(Instant.now());
        storage.upsertModel(model);
        router.invalidate();
        return toModelResponse(model, requireProvider(model.getProviderId()));
    }

    // ---- internals ----

    private AIProvider requireProvider(String id) {
        return storage.findProvider(id)
                .orElseThrow(() -> new ModelCenterException("PROVIDER_NOT_FOUND", "Provider 不存在: " + id));
    }

    private AIModel requireModel(String id) {
        return storage.findModel(id)
                .orElseThrow(() -> new ModelCenterException("MODEL_NOT_FOUND", "模型不存在: " + id));
    }

    /** apiKey null/blank = 不修改；非空 = 覆盖保存（加密落盘）。 */
    private void applyCredential(AIProvider provider, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        storage.credentials().save(provider.getId(), apiKey);
    }

    private void validateCapabilities(List<String> capabilities) {
        if (capabilities == null) {
            return;
        }
        for (String c : capabilities) {
            if (c != null && !ALLOWED_CAPABILITIES.contains(c.toUpperCase())) {
                throw new ModelCenterException("INVALID_REQUEST", "不支持的能力声明: " + c);
            }
        }
    }

    private List<String> normalizeCapabilities(List<String> capabilities) {
        if (capabilities == null) {
            return List.of(AIModel.CAP_CHAT, AIModel.CAP_STREAMING);
        }
        return capabilities.stream().map(String::toUpperCase).distinct().toList();
    }

    private static String normalizeBaseUrl(String url) {
        String stripped = url.strip();
        if (!stripped.startsWith("http://") && !stripped.startsWith("https://")) {
            throw new ModelCenterException("INVALID_REQUEST", "baseUrl 必须以 http:// 或 https:// 开头");
        }
        return stripped.endsWith("/") ? stripped.substring(0, stripped.length() - 1) : stripped;
    }

    /** Ollama 探测：/api/tags 可达 +（若给了 modelName）模型已安装。不消耗 token。 */
    private TestConnectionResponse testOllama(AIProvider provider, String modelName, long startedAt) {
        List<String> installed = availableModels(provider.getId()).models();
        long latency = System.currentTimeMillis() - startedAt;
        if (modelName != null && !installed.contains(modelName)) {
            return TestConnectionResponse.fail(provider.getName(), modelName, "MODEL_NOT_FOUND",
                    "模型未安装（本机 Ollama 中未找到 " + modelName + "）");
        }
        return TestConnectionResponse.ok(provider.getName(), modelName != null ? modelName : "ollama", latency);
    }

    /** OpenAI 兼容探测：最小 chat/completions 请求（max_tokens=16）。 */
    private TestConnectionResponse testOpenAiCompatible(AIProvider provider, String modelName, long startedAt) {
        if (modelName == null) {
            throw new ModelCenterException("INVALID_REQUEST", "请先填写要测试的 Model ID");
        }
        CredentialResolver.ResolvedCredential credential = credentialResolver.resolve(provider);
        if (credential.key() == null) {
            return TestConnectionResponse.fail(provider.getName(), modelName, "NOT_CONFIGURED",
                    "尚未配置 API Key（Credential Source: " + credential.source() + "）");
        }
        AiProperties.Rag rag = aiProperties.ragOrDefault();
        ChatModelAdapter adapter = new OpenAiCompatibleChatModelAdapter(
                provider.getBaseUrl(), credential.key(), modelName, rag.llmTimeoutSeconds());
        adapter.complete("You are a connection health check.", "ping", ChatOptions.probe());
        return TestConnectionResponse.ok(provider.getName(), modelName, System.currentTimeMillis() - startedAt);
    }

    // ---- response mapping（Key 绝不出现在任何 Response） ----

    private ProviderResponse toProviderResponse(AIProvider p) {
        CredentialResolver.ResolvedCredential credential = credentialResolver.resolve(p);
        boolean configured = credential.source() == CredentialStore.Source.USER_CONFIGURED
                || credential.source() == CredentialStore.Source.ENVIRONMENT;
        return new ProviderResponse(
                p.getId(), p.getName(), p.getType().name(), p.getBaseUrl(), p.isEnabled(),
                configured,
                configured ? CredentialResolver.mask(credential.key()) : null,
                credential.source().name(),
                p.getCreatedAt().toString(), p.getUpdatedAt().toString());
    }

    private ModelResponse toModelResponse(AIModel m, AIProvider provider) {
        return new ModelResponse(
                m.getId(), m.getProviderId(),
                provider != null ? provider.getName() : "?",
                m.getModelName(), m.getDisplayName(), m.getCapabilities(),
                m.isEnabled(), m.isDefault(),
                m.getCreatedAt().toString(), m.getUpdatedAt().toString());
    }
}
