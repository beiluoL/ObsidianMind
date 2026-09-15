package com.obsidianmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Milvus 连接与 Collection 配置。未启动 Milvus 不影响主应用（健康检查按需探测）。
 * collection：固定名称惰性创建；vectorDimension：来源与校验见 docs/architecture/milvus.md。
 */
@ConfigurationProperties(prefix = "milvus")
public record MilvusProperties(
        String host,
        int port,
        int timeoutSeconds,
        @DefaultValue("obsidianmind_chunks") String collection,
        @DefaultValue("1024") int vectorDimension) {

    /** 归一化访问：直接 new 构造（测试）时兜底。 */
    public MilvusProperties {
        if (collection == null || collection.isBlank()) {
            collection = "obsidianmind_chunks";
        }
        if (vectorDimension <= 0) {
            vectorDimension = 1024;
        }
    }
}
