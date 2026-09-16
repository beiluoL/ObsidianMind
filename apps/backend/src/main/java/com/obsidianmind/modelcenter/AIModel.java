package com.obsidianmind.modelcenter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * AI Model（Phase 5.5 Model Center）：Provider 下的一个具体模型条目。
 * capabilities 只允许用户在配置中显式声明（CHAT / STREAMING / REASONING），系统不猜测能力；
 * isDefault 全局唯一（ModelCenterStorage 保证互斥）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AIModel {

    public static final String CAP_CHAT = "CHAT";
    public static final String CAP_STREAMING = "STREAMING";
    public static final String CAP_REASONING = "REASONING";

    private String id;
    private String providerId;
    private String modelName;
    private String displayName;
    private List<String> capabilities = new ArrayList<>();
    private boolean enabled = true;
    private boolean isDefault = false;
    private Instant createdAt;
    private Instant updatedAt;

    public AIModel() {
    }

    public AIModel(String id, String providerId, String modelName, String displayName,
                   List<String> capabilities, boolean enabled, boolean isDefault,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.providerId = providerId;
        this.modelName = modelName;
        this.displayName = displayName;
        this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities);
        this.enabled = enabled;
        this.isDefault = isDefault;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
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

    public AIModel copy() {
        return new AIModel(id, providerId, modelName, displayName, capabilities,
                enabled, isDefault, createdAt, updatedAt);
    }
}
