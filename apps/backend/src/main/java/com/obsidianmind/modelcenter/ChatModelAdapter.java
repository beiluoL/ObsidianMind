package com.obsidianmind.modelcenter;

import com.obsidianmind.service.LLMService.LlmStreamListener;

/**
 * 统一 Chat 模型执行接口（Phase 5.5）：RAG / 测试连接只依赖它，不感知 Provider 差异。
 * 与 Phase 5 的 LLMService 同构（complete / streamComplete），但由 ModelRouter 按配置动态构造，
 * 而不是 Spring 单例——每个 (baseUrl, credential, model) 组合一个实例。
 * Provider 差异（Ollama NDJSON vs OpenAI SSE）全部封装在实现里，前端只见统一事件流。
 */
public interface ChatModelAdapter {

    /** 生成补全（非流式，用于测试连接等小请求）。 */
    String complete(String systemPrompt, String userMessage, ChatOptions options);

    /**
     * 流式生成（阻塞调用线程直到结束 / 取消 / 出错），语义与 LLMService.streamComplete 一致。
     */
    void streamComplete(String systemPrompt, String userMessage, ChatOptions options, LlmStreamListener listener);

    /** 供路由缓存命中判断与测试断言：本适配器绑定的关键参数指纹（绝不含 Key 原文）。 */
    String fingerprint();

    /** 生成参数（temperature / maxTokens / think）。 */
    record ChatOptions(double temperature, int maxTokens, boolean think) {

        public static ChatOptions of(double temperature, int maxTokens) {
            return new ChatOptions(temperature, maxTokens, false);
        }

        /** 最小测试请求参数：几分之一 token 就能验证连通性与认证。 */
        public static ChatOptions probe() {
            return new ChatOptions(0.0, 16, false);
        }
    }
}
