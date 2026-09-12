package com.obsidianmind.service;

import java.util.List;

/**
 * Embedding 抽象 —— Phase 2 索引链路（Chunk → Embedding → Milvus）的入口。
 * 模型必须配置化（OLLAMA_EMBEDDING_MODEL 等环境变量可覆盖）。
 */
public interface EmbeddingService {

    String provider();

    String modelName();

    boolean isAvailable();

    /** 批量向量化，返回与输入顺序一致的向量列表。 */
    List<float[]> embed(List<String> texts);
}
