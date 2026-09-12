package com.obsidianmind.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidianmind.config.AiProperties;
import com.obsidianmind.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ollama Embedding 实现：POST /api/embed 批量向量化。
 * Phase 2 索引链路使用；模型由 OLLAMA_EMBEDDING_MODEL 配置。
 */
@Component
public class OllamaEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingService.class);

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaEmbeddingService(AiProperties properties) {
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
    public String modelName() {
        return properties.ollama().embeddingModel();
    }

    @Override
    public boolean isAvailable() {
        try {
            embed(List.of("ping"));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        try {
            String body = restClient.post().uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.ollama().embeddingModel(),
                            "input", texts))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode embeddings = root.path("embeddings");
            List<float[]> vectors = new ArrayList<>();
            for (JsonNode vectorNode : embeddings) {
                float[] vector = new float[vectorNode.size()];
                for (int i = 0; i < vectorNode.size(); i++) {
                    vector[i] = (float) vectorNode.get(i).asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (RuntimeException e) {
            log.warn("Ollama Embedding 调用失败: {}", e.getMessage());
            throw new com.obsidianmind.exception.OllamaUnavailableException("Ollama 不可用: " + e.getMessage());
        } catch (Exception e) {
            throw new com.obsidianmind.exception.OllamaUnavailableException("Embedding 响应解析失败");
        }
    }
}
