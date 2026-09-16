package com.obsidianmind.modelcenter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * AI Provider（Phase 5.5 Model Center）：一个可调用大模型的服务接入点。
 * 与 Model（具体模型条目）分离——Provider 只描述「连哪里、怎么认证」，不描述模型。
 * 实例被 JSON 文件持久化（ModelCenterStorage），因此是可变 POJO 而非 record。
 * 注意：本类绝不持有 API Key——凭据在 CredentialStore（独立加密文件），防止随配置导出泄露。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AIProvider {

    /** Provider 类型。新类型 = 新枚举值 + CredentialResolver 允许的环境变量（如有）+ 前端图标，不需要新类。 */
    public enum Type {
        OLLAMA, DEEPSEEK, DASHSCOPE, OPENAI_COMPATIBLE
    }

    private String id;
    private String name;
    private Type type;
    private String baseUrl;
    private boolean enabled = true;
    private Instant createdAt;
    private Instant updatedAt;

    public AIProvider() {
    }

    public AIProvider(String id, String name, Type type, String baseUrl, boolean enabled,
                      Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** 深拷贝：CRUD 更新时先拷贝再改，避免半途失败污染持久化对象。 */
    public AIProvider copy() {
        return new AIProvider(id, name, type, baseUrl, enabled, createdAt, updatedAt);
    }
}
