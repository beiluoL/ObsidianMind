package com.obsidianmind.modelcenter;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.ModelCenterProperties;
import com.obsidianmind.exception.ModelCenterException;
import com.obsidianmind.service.LLMService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * ModelRouter 单元测试（Phase 5.5）：
 * 默认模型路由 / modelId 显式路由 / 未配置错误 / 禁用拦截 / 缓存失效（换 Key 立即生效）。
 */
class ModelRouterTest {

    @TempDir
    Path tempDir;

    private ModelCenterStorage storage;
    private ModelRouter router;
    private AiProperties ai;

    @BeforeEach
    void setUp() {
        ai = new AiProperties("ollama",
                new AiProperties.Ollama("http://localhost:11434", "qwen3.5:9b", "bge-m3", 3),
                null, null,
                new AiProperties.Retrieval(5, 20, 0, 2, 200, 512),
                new AiProperties.Rag(6, 12000, 0.1, 1024, false, 120, 180));
        storage = new ModelCenterStorage(new ModelCenterProperties(tempDir.toString()), ai);
        router = new ModelRouter(storage, new CredentialResolver(storage), mock(LLMService.class), ai);
    }

    private void seedModel(String modelId, String providerId, String modelName, boolean isDefault, boolean enabled) {
        AIModel m = new AIModel(modelId, providerId, modelName, modelName,
                List.of("CHAT", "STREAMING"), enabled, isDefault, Instant.now(), Instant.now());
        storage.upsertModel(m);
        router.invalidate();
    }

    @Test
    void shouldFallbackToLegacyOllamaWhenNoDefault() {
        ModelRouter.ResolvedModel resolved = router.resolve(null);
        assertThat(resolved.providerId()).isEqualTo("ollama");
        assertThat(resolved.modelName()).isEqualTo("qwen3.5:9b");
        assertThat(resolved.adapter().fingerprint()).startsWith("legacy-ollama|");
    }

    @Test
    void shouldRouteExplicitModelId() {
        seedModel("m1", "deepseek", "deepseek-v4-flash", false, true);
        storage.credentials().save("deepseek", "sk-router-test-key-1234");
        router.invalidate();
        ModelRouter.ResolvedModel resolved = router.resolve("m1");
        assertThat(resolved.providerId()).isEqualTo("deepseek");
        assertThat(resolved.modelName()).isEqualTo("deepseek-v4-flash");
        // OpenAI 兼容适配器
        assertThat(resolved.adapter().fingerprint()).contains("deepseek-v4-flash");
    }

    @Test
    void shouldThrowModelNotFoundForUnknownId() {
        assertThatThrownBy(() -> router.resolve("no-such-model"))
                .isInstanceOfSatisfying(ModelCenterException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("MODEL_NOT_FOUND");
                    assertThat(e.getMessage()).contains("no-such-model");
                });
    }

    @Test
    void shouldThrowWhenProviderNotConfigured() {
        seedModel("m2", "dashscope", "deepseek-v4-pro", true, true);
        // DashScope 无用户凭据；测试进程环境变量无法保证存在——用删除凭据的方式保证 NONE 分支
        storage.credentials().delete("dashscope");
        // 若 CI 环境恰好有 DASHSCOPE_API_KEY，resolve 会走 ENVIRONMENT 分支成功；此处仅在无 env 时断言
        String env = System.getenv("DASHSCOPE_API_KEY");
        if (env == null || env.isBlank()) {
            assertThatThrownBy(() -> router.resolve("m2"))
                    .isInstanceOfSatisfying(ModelCenterException.class,
                            e -> assertThat(e.getCode()).isEqualTo("PROVIDER_NOT_CONFIGURED"));
        }
    }

    @Test
    void shouldThrowWhenModelOrProviderDisabled() {
        seedModel("m3", "deepseek", "deepseek-v4-pro", true, false);
        assertThatThrownBy(() -> router.resolve("m3"))
                .isInstanceOfSatisfying(ModelCenterException.class,
                        e -> assertThat(e.getCode()).isEqualTo("MODEL_DISABLED"));

        seedModel("m4", "deepseek", "deepseek-v4-pro", true, true);
        AIProvider p = storage.findProvider("deepseek").orElseThrow();
        p.setEnabled(false);
        storage.upsertProvider(p);
        router.invalidate();
        assertThatThrownBy(() -> router.resolve("m4"))
                .isInstanceOfSatisfying(ModelCenterException.class,
                        e -> assertThat(e.getCode()).isEqualTo("PROVIDER_DISABLED"));
    }

    @Test
    void shouldInvalidateCacheOnCredentialChange() {
        seedModel("m5", "deepseek", "deepseek-v4-flash", true, true);
        storage.credentials().save("deepseek", "sk-old-key-aaaa");

        ModelRouter.ResolvedModel first = router.resolve("m5");
        String oldFingerprint = first.adapter().fingerprint();

        // 换 Key → invalidate → 新 adapter（fingerprint 含凭据哈希，必然不同）
        storage.credentials().save("deepseek", "sk-new-key-bbbb");
        router.invalidate();
        ModelRouter.ResolvedModel second = router.resolve("m5");

        assertThat(second.adapter().fingerprint()).isNotEqualTo(oldFingerprint);
    }
}
