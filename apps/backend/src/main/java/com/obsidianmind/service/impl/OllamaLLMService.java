package com.obsidianmind.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidianmind.config.AiProperties;
import com.obsidianmind.exception.OllamaUnavailableException;
import com.obsidianmind.service.LLMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
}
