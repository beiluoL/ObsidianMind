package com.obsidianmind.modelcenter;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.exception.ModelCenterException;
import com.obsidianmind.service.LLMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型路由器（Phase 5.5）：RAG 只知道「modelId → ChatModelAdapter」，不感知 Provider 细节。
 *
 * 解析顺序（ChatRequest.modelId）：
 *   ① 显式 modelId → 指定模型
 *   ② null → Model Center 默认模型（isDefault 且 enabled）
 *   ③ ①② 均无 → legacy 回退：配置文件里的 Ollama Chat 模型（保持 Phase 5 行为不变，老用户零迁移）
 *
 * 缓存与失效：adapter 按 (provider 配置 + 凭据指纹) 缓存；任何 Provider/Model 写操作触发全量失效
 * （简单正确优先——Model Center 写操作频率极低，全量失效无性能影响）。用户换 Key 后旧客户端立刻作废。
 */
@Component
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final ModelCenterStorage storage;
    private final CredentialResolver credentialResolver;
    private final LLMService legacyOllamaService;
    private final AiProperties aiProperties;
    private final Map<String, ChatModelAdapter> cache = new ConcurrentHashMap<>();

    public ModelRouter(ModelCenterStorage storage, CredentialResolver credentialResolver,
                       LLMService legacyOllamaService, AiProperties aiProperties) {
        this.storage = storage;
        this.credentialResolver = credentialResolver;
        this.legacyOllamaService = legacyOllamaService;
        this.aiProperties = aiProperties;
    }

    /** 路由结果：执行器 + 描述信息（供日志 / 前端展示，不含凭据）。 */
    public record ResolvedModel(ChatModelAdapter adapter, String providerId, String providerName,
                                String modelName, String displayName) {
    }

    public ResolvedModel resolve(String modelId) {
        AIModel model;
        if (modelId != null && !modelId.isBlank()) {
            model = storage.findModel(modelId)
                    .orElseThrow(() -> new ModelCenterException("MODEL_NOT_FOUND", "模型不存在: " + modelId));
        } else {
            model = storage.defaultModel().orElse(null);
        }

        if (model == null) {
            return legacy(); // Model Center 尚未配置任何默认模型 → Phase 5 legacy Ollama 路径
        }
        if (!model.isEnabled()) {
            throw new ModelCenterException("MODEL_DISABLED", "该模型已被禁用");
        }
        AIProvider provider = storage.findProvider(model.getProviderId())
                .orElseThrow(() -> new ModelCenterException("PROVIDER_NOT_FOUND", "模型所属 Provider 不存在"));
        if (!provider.isEnabled()) {
            throw new ModelCenterException("PROVIDER_DISABLED", "该 Provider 已被禁用");
        }

        ChatModelAdapter adapter = adapterFor(provider, model);
        return new ResolvedModel(adapter, provider.getId(), provider.getName(),
                model.getModelName(), model.getDisplayName());
    }

    /** Model Center 任一写操作后调用：保证换 Key / 改 baseUrl 立即生效。 */
    public void invalidate() {
        cache.clear();
        log.debug("Model Router 缓存已失效");
    }

    /** legacy 回退：配置文件 Ollama（fingerprint 固定，跟随配置变化）。ollama 配置缺失时用安全默认值。 */
    private ResolvedModel legacy() {
        String baseUrl;
        String chatModel;
        try {
            baseUrl = aiProperties.ollama().baseUrl();
            chatModel = aiProperties.ollama().chatModel();
        } catch (NullPointerException | IllegalStateException e) {
            baseUrl = "http://localhost:11434";
            chatModel = "qwen3.5:9b";
        }
        String fp = "legacy-ollama|" + baseUrl + "|" + chatModel;
        ChatModelAdapter adapter = cache.computeIfAbsent(fp,
                k -> new OllamaChatModelAdapter(legacyOllamaService, fp));
        return new ResolvedModel(adapter, "ollama", "Ollama", chatModel, chatModel);
    }

    private ChatModelAdapter adapterFor(AIProvider provider, AIModel model) {
        CredentialResolver.ResolvedCredential credential = credentialResolver.resolve(provider);
        if (credential.source() == CredentialStore.Source.NONE
                && provider.getType() != AIProvider.Type.OLLAMA) {
            throw new ModelCenterException("PROVIDER_NOT_CONFIGURED",
                    "Provider「" + provider.getName() + "」尚未配置 API Key");
        }
        AiProperties.Rag rag = aiProperties.ragOrDefault();
        String fp = provider.getId() + "|" + provider.getBaseUrl() + "|" + model.getModelName()
                + "|cred#" + Integer.toHexString(credential.key() == null ? 0 : credential.key().hashCode());

        return cache.computeIfAbsent(fp, k -> switch (provider.getType()) {
            case OLLAMA -> new OllamaChatModelAdapter(legacyOllamaService, fp);
            case DEEPSEEK, DASHSCOPE, OPENAI_COMPATIBLE -> new OpenAiCompatibleChatModelAdapter(
                    provider.getBaseUrl(), credential.key(), model.getModelName(), rag.llmTimeoutSeconds());
        });
    }
}
