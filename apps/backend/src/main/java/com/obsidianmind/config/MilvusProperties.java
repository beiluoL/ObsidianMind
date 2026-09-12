package com.obsidianmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 连接配置。未启动 Milvus 不影响主应用（健康检查按需探测）。
 */
@ConfigurationProperties(prefix = "milvus")
public record MilvusProperties(String host, int port, int timeoutSeconds) {
}
