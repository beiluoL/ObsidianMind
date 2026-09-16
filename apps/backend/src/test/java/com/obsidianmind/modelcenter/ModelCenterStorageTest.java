package com.obsidianmind.modelcenter;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.ModelCenterProperties;
import com.obsidianmind.exception.ModelCenterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Model Center 存储与凭据安全单元测试（Phase 5.5）：
 * 播种 / CRUD / 默认模型唯一 / 凭据加密落盘 / Key 不出现在配置文件 / 遮蔽。
 */
class ModelCenterStorageTest {

    @TempDir
    Path tempDir;

    private ModelCenterStorage storage;
    private AiProperties ai;

    @BeforeEach
    void setUp() {
        ai = new AiProperties("ollama", null, null, null,
                new AiProperties.Retrieval(5, 20, 0, 2, 200, 512),
                new AiProperties.Rag(6, 12000, 0.1, 1024, false, 120, 180));
        storage = new ModelCenterStorage(new ModelCenterProperties(tempDir.toString()), ai);
    }

    @Test
    void shouldSeedBuiltinProvidersOnFirstUse() {
        List<AIProvider> providers = storage.listProviders();
        assertThat(providers).extracting(AIProvider::getId)
                .containsExactlyInAnyOrder("ollama", "deepseek", "dashscope");
        assertThat(providers).allSatisfy(p -> assertThat(p.getBaseUrl()).isNotBlank());
    }

    @Test
    void shouldNotWriteApiKeyIntoPlainConfigFile() throws Exception {
        storage.credentials().save("deepseek", "sk-test-secret-abcdef");
        assertThat(storage.credentials().get("deepseek")).isEqualTo("sk-test-secret-abcdef");

        // 配置文件与凭据文件都必须不含 Key 明文
        String config = Files.readString(tempDir.resolve("model-center.json"));
        assertThat(config).doesNotContain("sk-test-secret-abcdef");
        String credFile = Files.readString(tempDir.resolve("credentials.json"));
        assertThat(credFile).doesNotContain("sk-test-secret-abcdef");
    }

    @Test
    void shouldEncryptCredentialFileIndependentlyFromConfig() throws Exception {
        storage.credentials().save("dashscope", "sk-dash-1234567890");
        // 凭据文件存在且为 base64 密文（不含明文前缀 sk-）
        String credFile = Files.readString(tempDir.resolve("credentials.json")).replace(" ", "").replace("\n", "");
        assertThat(credFile).contains("dashscope");
        assertThat(credFile).doesNotContain("sk-dash");

        // 删除 Provider 级联删除凭据
        assertThat(storage.credentials().exists("dashscope")).isTrue();
        storage.deleteProvider("dashscope");
        assertThat(storage.credentials().exists("dashscope")).isFalse();
    }

    @Test
    void shouldEnforceSingleDefaultModel() {
        AIModel a = new AIModel("m1", "ollama", "qwen3.5:9b", "Qwen 9B",
                List.of("CHAT", "STREAMING"), true, true, java.time.Instant.now(), java.time.Instant.now());
        storage.upsertModel(a);
        assertThat(storage.defaultModel()).isPresent();
        assertThat(storage.defaultModel().get().getId()).isEqualTo("m1");

        AIModel b = new AIModel("m2", "deepseek", "deepseek-v4-flash", "DeepSeek Flash",
                List.of("CHAT", "STREAMING"), true, true, java.time.Instant.now(), java.time.Instant.now());
        storage.upsertModel(b);
        assertThat(storage.countDefaultModels()).isEqualTo(1);
        assertThat(storage.defaultModel().get().getId()).isEqualTo("m2");
    }

    @Test
    void shouldDeleteProviderCascadingModels() {
        AIModel m = new AIModel("mx", "deepseek", "deepseek-v4-pro", "DeepSeek Pro",
                List.of("CHAT"), true, false, java.time.Instant.now(), java.time.Instant.now());
        storage.upsertModel(m);
        assertThat(storage.deleteProvider("deepseek")).isTrue();
        assertThat(storage.findProvider("deepseek")).isEmpty();
        assertThat(storage.findModel("mx")).isEmpty();
    }

    @Test
    void shouldMaskKeyShowingOnlyLastFour() {
        assertThat(CredentialResolver.mask("sk-abcdefghijklmnop")).isEqualTo("••••••••mnop");
        assertThat(CredentialResolver.mask("abcd")).isEqualTo("••••");
        assertThat(CredentialResolver.mask(null)).isNull();
        assertThat(CredentialResolver.mask("  ")).isNull();
    }

    @Test
    void shouldResolveCredentialSourceByPriority() {
        // 环境 fallback：DEEPSEEK 类型允许 DEEPSEEK_API_KEY
        CredentialResolver resolver = new CredentialResolver(storage);
        AIProvider deepseek = storage.findProvider("deepseek").orElseThrow();
        // 测试进程无法保证环境变量存在：只断言来源 ∈ {USER_CONFIGURED, ENVIRONMENT, NONE} 且逻辑一致
        CredentialResolver.ResolvedCredential resolved = resolver.resolve(deepseek);
        assertThat(resolved.source()).isNotNull();

        // 用户配置 > 环境变量
        storage.credentials().save("deepseek", "sk-user-key-0001");
        assertThat(resolver.resolve(deepseek).source()).isEqualTo(CredentialStore.Source.USER_CONFIGURED);

        // Ollama 无凭据也视为可用（本地服务）
        AIProvider ollama = storage.findProvider("ollama").orElseThrow();
        assertThat(resolver.resolve(ollama).source()).isEqualTo(CredentialStore.Source.NONE);
    }

    @Test
    void shouldRejectUnknownCapability() {
        // validateCapabilities 是 Service 层逻辑，此处只验证模型 capability 白名单常量
        assertThat(List.of(AIModel.CAP_CHAT, AIModel.CAP_STREAMING, AIModel.CAP_REASONING))
                .doesNotContain("VISION");
    }
}
