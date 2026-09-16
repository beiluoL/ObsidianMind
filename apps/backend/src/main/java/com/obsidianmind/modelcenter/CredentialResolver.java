package com.obsidianmind.modelcenter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 凭据解析器（Phase 5.5）：User Configured > Environment > NONE。
 *
 * 环境变量回退只用于开发 / 测试，且按 Provider 类型严格隔离：
 *   DEEPSEEK  → DEEPSEEK_API_KEY
 *   DASHSCOPE → DASHSCOPE_API_KEY
 * OLLAMA / OPENAI_COMPATIBLE 无标准环境变量，不参与回退。
 * 禁止跨类型取用（如 DeepSeek Direct 拿 DASHSCOPE_API_KEY）。
 *
 * 安全边界：本类只在 JVM 内传值——返回值进入 HTTP Authorization 头，绝不落日志 / 异常 / 配置文件。
 */
@Component
public class CredentialResolver {

    private static final Logger log = LoggerFactory.getLogger(CredentialResolver.class);

    private final CredentialStore credentialStore;

    public CredentialResolver(ModelCenterStorage storage) {
        this.credentialStore = storage.credentials();
    }

    /** 允许环境变量回退的 Provider 类型 → 环境变量名。 */
    static String envVarFor(AIProvider.Type type) {
        return switch (type) {
            case DEEPSEEK -> "DEEPSEEK_API_KEY";
            case DASHSCOPE -> "DASHSCOPE_API_KEY";
            default -> null;
        };
    }

    /**
     * 解析某 Provider 当前生效的凭据来源与值。
     *
     * @return key 原文（只允许进入请求头）+ 来源；NONE 时 key 为 null
     */
    public ResolvedCredential resolve(AIProvider provider) {
        // ① 用户配置优先（加密文件）
        String userKey = credentialStore.get(provider.getId());
        if (userKey != null && !userKey.isBlank()) {
            return new ResolvedCredential(userKey, CredentialStore.Source.USER_CONFIGURED);
        }
        // ② 环境变量回退（仅 DEEPSEEK / DASHSCOPE，按类型隔离）
        String envVar = envVarFor(provider.getType());
        if (envVar != null) {
            String envKey = System.getenv(envVar);
            if (envKey != null && !envKey.isBlank()) {
                log.info("Provider {} 使用环境变量凭据 {}", provider.getId(), envVar);
                return new ResolvedCredential(envKey, CredentialStore.Source.ENVIRONMENT);
            }
        }
        // ③ Ollama 等无需凭据的类型视为可用（本地服务无认证）
        if (provider.getType() == AIProvider.Type.OLLAMA) {
            return new ResolvedCredential(null, CredentialStore.Source.NONE);
        }
        return new ResolvedCredential(null, CredentialStore.Source.NONE);
    }

    /** 暴露给 UI 的状态：显示来源，绝不显示值。 */
    public CredentialStore.Source sourceOf(AIProvider provider) {
        return resolve(provider).source();
    }

    /**
     * 遮蔽 Key：只保留末 4 位。null 返回 null（表示未配置，与「已配置但被遮蔽」区分）。
     */
    public static String mask(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String stripped = key.strip();
        if (stripped.length() <= 4) {
            return "••••";
        }
        return "••••••••" + stripped.substring(stripped.length() - 4);
    }

    /** 解析结果。key 为原文——调用方仅可用于构造请求头。 */
    public record ResolvedCredential(String key, CredentialStore.Source source) {
    }
}
