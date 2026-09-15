package com.obsidianmind.service;

/**
 * LLM 抽象 —— 业务代码禁止直接依赖 Ollama 客户端。
 * 未来可切换 OpenAI / DeepSeek / Qwen 等 OpenAI Compatible Provider。
 */
public interface LLMService {

    String provider();

    boolean isAvailable();

    /** 生成补全。Provider 不可用时抛 OllamaUnavailableException（由实现决定）。 */
    String complete(String systemPrompt, String userMessage);

    /**
     * 流式生成补全（Phase 5 RAG Chat）：阻塞调用线程直到生成结束 / 取消 / 出错。
     * onToken 逐 token 回调；isCancelled() 由调用方轮询（客户端断连时返回 true），
     * 实现应尽快停止拉取上游流——连接关闭后 Ollama 会中止生成。
     *
     * @throws com.obsidianmind.exception.OllamaUnavailableException 连接失败 / 响应畸形
     * @throws com.obsidianmind.exception.LlmTimeoutException        读超时（首 token 或 token 间隔）
     */
    void streamComplete(String systemPrompt, String userMessage, LlmStreamListener listener);

    /** 流式监听器：token 回调 + 取消轮询。 */
    interface LlmStreamListener {

        void onToken(String token);

        boolean isCancelled();
    }
}
