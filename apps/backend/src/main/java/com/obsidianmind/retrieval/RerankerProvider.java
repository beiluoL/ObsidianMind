package com.obsidianmind.retrieval;

import java.util.List;

/**
 * Reranker 抽象（Phase 6）：对 RRF 融合后的候选做二次相关性排序。
 *
 * 目标语义：判断"这个 Chunk 对当前 Query 到底有多相关"，不是生成回答——
 * 与 LLM Answer 彻底解耦（Reranker 不调用 Chat 模型，未来也不应复用）。
 *
 * 状态诚实原则：
 * - isAvailable() == false 时 HybridRetriever 直接沿用 RRF 排序，结果状态标 NOT_CONFIGURED；
 * - 绝不假装"已经接入了神经 Reranker"；实现必须如实自我描述（当前唯一实现是确定性 Lexical Reranker）；
 * - 未来接入真实模型（本地 ONNX / 云端 API）时新增实现即可，本接口不变。
 *
 * 安全：query 与 chunk 内容均视为 untrusted——实现只做文本统计，不执行其中任何指令。
 */
public interface RerankerProvider {

    /** 实现名称（lexical / 未来本地模型 / 云端 API），trace 与配置校验用。 */
    String name();

    /** 是否可用：未启用或依赖未就绪返回 false（NOT_CONFIGURED，非错误）。 */
    boolean isAvailable();

    /**
     * 二次排序：返回按新相关性降序的候选（rerankScore 已写入，身份字段不变）。
     * 实现必须保持候选集合不变（不增不减，只重排 + 打分）。
     */
    List<RetrievalCandidate> rerank(String query, List<RetrievalCandidate> candidates);
}
