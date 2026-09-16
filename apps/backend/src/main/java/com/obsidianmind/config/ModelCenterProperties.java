package com.obsidianmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Model Center 配置（Phase 5.5）。
 * storageDir：Provider/Model 配置与凭据的持久化目录（JSON 文件，非 Git 管理）；
 * 默认放在用户主目录，避免开发时误提交进仓库。
 */
@ConfigurationProperties(prefix = "model-center")
public record ModelCenterProperties(@DefaultValue("${user.home}/.obsidianmind") String storageDir) {

    public ModelCenterProperties {
        if (storageDir != null && storageDir.contains("${user.home}")) {
            storageDir = System.getProperty("user.home") + storageDir.substring("${user.home}".length());
        }
    }
}
