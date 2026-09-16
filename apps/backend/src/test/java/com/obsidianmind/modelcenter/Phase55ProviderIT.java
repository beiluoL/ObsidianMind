package com.obsidianmind.modelcenter;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.ModelCenterProperties;
import com.obsidianmind.service.ModelCenterService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Provider 集成测试（Phase 5.5）。
 * 运行：mvn test -Dtest=Phase55ProviderIT -Dprovider.it=1
 *
 * 安全铁律（本测试自我约束）：
 *  - 只做 env presence check（是否非空），绝不打印 / 记录 Key 内容；
 *  - Key 只在 JVM 内通过 CredentialResolver 环境回退进入 HTTP 头；
 *  - 断言只针对 safe result（success / latency / errorCode），不针对原始响应体。
 *
 * 覆盖：
 *  1. Ollama 真实可用性（本机 :11434）+ 已安装模型读取；
 *  2. DashScope 真实 Test Connection（DASHSCOPE_API_KEY 环境回退 → deepseek-v4-flash）。
 */
class Phase55ProviderIT {

    @TempDir
    Path tempDir;

    private AiProperties ai;
    private ModelCenterStorage storage;
    private ModelRouter router;
    private ModelCenterService service;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue("1".equals(System.getProperty("provider.it")),
                "-Dprovider.it=1 未设置，跳过真实 Provider IT");
        ai = new AiProperties("ollama",
                new AiProperties.Ollama("http://localhost:11434", "qwen3.5:9b", "bge-m3", 3),
                null, null,
                new AiProperties.Retrieval(5, 20, 0, 2, 200, 512),
                new AiProperties.Rag(6, 12000, 0.1, 1024, false, 60, 180));
        storage = new ModelCenterStorage(new ModelCenterProperties(tempDir.toString()), ai);
        router = new ModelRouter(storage, new CredentialResolver(storage),
                new com.obsidianmind.service.impl.OllamaLLMService(ai), ai);
        service = new ModelCenterService(storage, new CredentialResolver(storage), router, ai);
    }

    @Test
    void ollamaShouldBeReachableAndListModels() {
        var available = service.availableModels("ollama");
        assertThat(available.models()).isNotEmpty();
        // Key 永不出现在结果里
        assertThat(String.join(",", available.models())).doesNotContain("sk-");
    }

    @Test
    void dashscopeEnvKeyShouldPassRealTestConnection() {
        // presence check only：不输出 Key 内容
        String env = System.getenv("DASHSCOPE_API_KEY");
        Assumptions.assumeTrue(env != null && !env.isBlank(),
                "DASHSCOPE_API_KEY 不存在，跳过 DashScope 真实测试");
        Assumptions.assumeTrue(storage.credentials().get("dashscope") == null,
                "凭据存储已有 dashscope 用户 Key，跳过环境回退验证");

        // 用户未配置 → 环境变量回退（ENVIRONMENT source），真实调用 DashScope 兼容模式
        var result = service.testProvider("dashscope",
                new com.obsidianmind.dto.modelcenter.ModelCenterDtos.TestConnectionRequest("deepseek-v4-flash"));

        assertThat(result.success())
                .as("DashScope 测试连接应成功（safe message: %s）", result.message())
                .isTrue();
        assertThat(result.latencyMs()).isPositive();
        assertThat(result.message()).doesNotContain(env).doesNotContain("Bearer");
        // 环境回退绝不写库
        assertThat(storage.credentials().get("dashscope")).isNull();
    }
}
