package com.obsidianmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用配置项。所有敏感/环境相关值均可通过环境变量覆盖，禁止硬编码。
 */
@ConfigurationProperties(prefix = "obsidian")
public record ObsidianProperties(Vault vault) {

    public record Vault(String path) {
    }
}
