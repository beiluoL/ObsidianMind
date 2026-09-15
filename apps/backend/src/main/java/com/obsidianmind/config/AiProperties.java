package com.obsidianmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 运行时配置：provider 可切换（ollama / 未来 openai / deepseek 等 OpenAI Compatible API）。
 * chunk / embedding 为 Phase 3 知识管线参数（详见 docs/architecture/knowledge-pipeline.md）。
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(String provider, Ollama ollama, Chunk chunk, Embedding embedding, Retrieval retrieval, Rag rag) {

    public record Ollama(String baseUrl, String chatModel, String embeddingModel, int timeoutSeconds) {
    }

    /** Chunking 参数：size=单块字符上限，overlap=相邻块重叠字符数（要求 overlap < size）。 */
    public record Chunk(@DefaultValue("800") int size, @DefaultValue("100") int overlap) {
    }

    /** Embedding 参数：batchSize=单次 HTTP 携带的 Chunk 数；timeoutSeconds=请求超时（冷启动需宽于健康探测）。 */
    public record Embedding(@DefaultValue("16") int batchSize, @DefaultValue("30") int timeoutSeconds) {
    }

    /**
     * 检索参数（Phase 4）。scoreThreshold 默认 0（关闭）：COSINE 分布随模型而异，
     * 阈值必须来自评估集实测（docs/architecture/retrieval-evaluation.md），禁止拍脑袋设值静默丢结果。
     */
    public record Retrieval(
            @DefaultValue("5") int defaultTopK,
            @DefaultValue("20") int maxTopK,
            @DefaultValue("0") double scoreThreshold,
            @DefaultValue("2") int maxPerDocument,
            @DefaultValue("200") int snippetLength,
            @DefaultValue("512") int maxQueryLength) {
    }

    /**
     * RAG 问答参数（Phase 5）。
     * maxChunks / maxChars：进入 Prompt 的 Context 上限——TopK 再大也只保留相似度最高的前 maxChunks 条，
     * 且总字符超 maxChars 时按相似度排名整体截断（宁缺勿滥，防上下文污染与巨型 Prompt）；
     * temperature：RAG 问答要求贴资料，取低值，不拍脑袋写死在业务代码；
     * maxTokens：num_predict 上限，防异常大输出；
     * think：是否让 thinking 模型先推理再作答——RAG 贴资料场景默认关闭
     * （qwen3.5 实测：开启时推理 token 先行，会把 maxTokens 预算耗尽导致正文为空）；
     * llmTimeoutSeconds：流式请求读超时（首 token 与 token 间隔共用）；
     * streamTimeoutSeconds：SseEmitter 整体生命周期超时。
     */
    public record Rag(
            @DefaultValue("6") int maxChunks,
            @DefaultValue("12000") int maxChars,
            @DefaultValue("0.1") double temperature,
            @DefaultValue("1024") int maxTokens,
            @DefaultValue("false") boolean think,
            @DefaultValue("120") int llmTimeoutSeconds,
            @DefaultValue("180") int streamTimeoutSeconds) {
    }

    /** 归一化访问：配置缺省时提供安全默认值（@DefaultValue 仅对 yml 绑定生效，直接 new 仍可能为 null）。 */
    public Chunk chunkOrDefault() {
        return chunk != null ? chunk : new Chunk(800, 100);
    }

    public Embedding embeddingOrDefault() {
        return embedding != null ? embedding : new Embedding(16, 30);
    }

    public Retrieval retrievalOrDefault() {
        return retrieval != null ? retrieval : new Retrieval(5, 20, 0, 2, 200, 512);
    }

    public Rag ragOrDefault() {
        return rag != null ? rag : new Rag(6, 12000, 0.1, 1024, false, 120, 180);
    }
}
