package com.obsidianmind.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidianmind.config.AiProperties;
import com.obsidianmind.exception.LlmTimeoutException;
import com.obsidianmind.exception.OllamaUnavailableException;
import com.obsidianmind.service.LLMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Ollama LLM 实现：/api/chat 非流式补全 + /api/tags 健康探测。
 * Ollama 未启动时 isAvailable() 返回 false，不阻塞应用启动。
 */
@Component
public class OllamaLLMService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(OllamaLLMService.class);

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaLLMService(AiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.ollama().baseUrl())
                .build();
    }

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public boolean isAvailable() {
        try {
            restClient.get().uri("/api/tags")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 非流式补全：POST /api/chat，固定两段消息（system + user），解析响应 message.content。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return 模型生成文本；响应缺字段时返回空串
     * @throws OllamaUnavailableException 请求失败或响应解析失败
     */
    @Override
    public String complete(String systemPrompt, String userMessage) {
        try {
            String body = restClient.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.ollama().chatModel(),
                            "stream", false,
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userMessage))))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            return root.path("message").path("content").asText("");
        } catch (RuntimeException e) {
            log.warn("Ollama 调用失败: {}", e.getMessage());
            throw new OllamaUnavailableException("Ollama 不可用: " + e.getMessage());
        } catch (Exception e) {
            throw new OllamaUnavailableException("Ollama 响应解析失败");
        }
    }

    public String chatModel() {
        return properties.ollama().chatModel();
    }

    public Duration timeout() {
        return Duration.ofSeconds(properties.ollama().timeoutSeconds());
    }

    /**
     * 流式补全（Phase 5）：POST /api/chat stream=true，逐行读 NDJSON，每行解析出增量 content 回调。
     * 读超时（首 token 与 token 间隔共用）= ai.rag.llm-timeout-seconds；取消时直接停止读流，
     * 连接随之关闭，Ollama 服务端会中止本次生成。
     */
    @Override
    public void streamComplete(String systemPrompt, String userMessage, LlmStreamListener listener) {
        AiProperties.Rag rag = properties.ragOrDefault();
        // 独立 streamClient：读超时按 RAG 生成场景放宽（complete() 的默认工厂不受影响）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(rag.llmTimeoutSeconds() * 1000);
        RestClient streamClient = RestClient.builder()
                .baseUrl(properties.ollama().baseUrl())
                .requestFactory(factory)
                .build();
        // think=false：RAG 要求贴资料作答（详见 AiProperties.Rag.think 注释）；旧版 Ollama 忽略该字段
        Map<String, Object> body = Map.of(
                "model", properties.ollama().chatModel(),
                "stream", true,
                "think", rag.think(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)),
                "options", Map.of(
                        "temperature", rag.temperature(),
                        "num_predict", rag.maxTokens()));
        try {
            streamClient.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank()) {
                                    continue;
                                }
                                if (listener.isCancelled()) {
                                    return null; // 客户端断连：停止拉流，连接随 exchange 关闭
                                }
                                JsonNode node = objectMapper.readTree(line);
                                String content = node.path("message").path("content").asText("");
                                if (!content.isEmpty()) {
                                    listener.onToken(content);
                                }
                                if (node.path("done").asBoolean(false)) {
                                    return null;
                                }
                            }
                        }
                        return null;
                    });
        } catch (ResourceAccessException e) {
            // 超时在 JDK 层表现为 SocketTimeoutException 的包装
            Throwable cause = e.getCause();
            if (cause instanceof java.net.SocketTimeoutException) {
                throw new LlmTimeoutException("LLM 生成超时（" + rag.llmTimeoutSeconds() + "s）");
            }
            log.warn("Ollama 流式调用失败: {}", e.getMessage());
            throw new OllamaUnavailableException("Ollama 不可用: " + e.getMessage());
        } catch (OllamaUnavailableException | LlmTimeoutException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Ollama 流式响应异常: {}", e.getMessage());
            throw new OllamaUnavailableException("Ollama 流式响应失败: " + e.getMessage());
        }
    }
}
