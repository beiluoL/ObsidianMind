package com.obsidianmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 配置：开发环境默认放行本地前端来源，生产环境通过 CORS_ALLOWED_ORIGINS 显式收敛。
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {
}
