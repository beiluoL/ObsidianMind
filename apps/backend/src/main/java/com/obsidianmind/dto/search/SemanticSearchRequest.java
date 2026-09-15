package com.obsidianmind.dto.search;

/**
 * 语义检索请求。topK 可选（null = 服务端默认）；不暴露 nProbe / metricType 等 Milvus 底层参数。
 */
public record SemanticSearchRequest(String query, Integer topK) {
}
