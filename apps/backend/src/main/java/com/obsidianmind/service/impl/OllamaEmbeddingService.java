package com.obsidianmind.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidianmind.config.AiProperties;
import com.obsidianmind.exception.EmbeddingException;
import com.obsidianmind.exception.OllamaUnavailableException;
import com.obsidianmind.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ollama Embedding 实现：POST /api/embed 批量向量化（Text → Dense Vector）。
 *
 * 契约（见 docs/architecture/embedding.md）：
 * - 输入顺序 = 输出顺序，一一对应，数量不符即失败；
 * - 向量维度一致且非空，首个向量维度即本库维度基准；
 * - 请求超时 ai.embedding.timeout-seconds（默认 30s，宽于健康探测的 3s——冷启动模型加载耗时）；
 * - 请求失败重试 1 次（500ms 退避），仍失败抛 OllamaUnavailableException；响应畸形抛 EmbeddingException；
 * - 绝不输出向量数值 / 全文到日志。
 */
@Component
public class OllamaEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingService.class);
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MS = 500;

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    public OllamaEmbeddingService(AiProperties properties) {
        this.properties = properties;
        AiProperties.Embedding embedding = properties.embeddingOrDefault();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(embedding.timeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.ollama().baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** 测试专用构造器：直接注入（可绑定 MockRestServiceServer 的）RestClient。 */
    OllamaEmbeddingService(AiProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
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

    /**
     * 批量向量化。
     *
     * @param texts 输入文本列表（禁止 null 元素；调用方保证非空文本——Chunker 已保证空文档产 0 Chunk）
     * @return 与输入一一对应的向量列表
     * @throws OllamaUnavailableException 连接失败 / 超时 / 重试后仍失败
     * @throws EmbeddingException        响应畸形、数量不符、向量维度异常
     */
    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return doEmbed(texts);
            } catch (OllamaUnavailableException e) {
                lastFailure = e;
                log.warn("Ollama Embedding 第 {}/{} 次调用失败: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new OllamaUnavailableException("Embedding 重试被中断");
                    }
                }
            }
        }
        throw lastFailure;
    }

    private List<float[]> doEmbed(List<String> texts) {
        String body;
        try {
            body = restClient.post().uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.ollama().embeddingModel(),
                            "input", texts))
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new OllamaUnavailableException("Ollama Embedding 请求失败: " + rootMessage(e));
        }
        if (body == null || body.isBlank()) {
            throw new EmbeddingException("Ollama Embedding 响应为空");
        }
        JsonNode embeddings;
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.hasNonNull("error")) {
                throw new EmbeddingException("Ollama 模型错误（可能未 pull）: " + root.path("error").asText());
            }
            embeddings = root.path("embeddings");
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("Ollama Embedding 响应解析失败");
        }
        if (!embeddings.isArray() || embeddings.size() != texts.size()) {
            throw new EmbeddingException("Embedding 数量不符: 期望 " + texts.size() + " 实际 " + embeddings.size());
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        int expectedDim = -1;
        for (JsonNode vectorNode : embeddings) {
            if (!vectorNode.isArray() || vectorNode.isEmpty()) {
                throw new EmbeddingException("Embedding 含空向量");
            }
            float[] vector = new float[vectorNode.size()];
            for (int i = 0; i < vectorNode.size(); i++) {
                vector[i] = (float) vectorNode.get(i).asDouble();
            }
            if (expectedDim < 0) {
                expectedDim = vector.length;
            } else if (vector.length != expectedDim) {
                throw new EmbeddingException("Embedding 维度不一致: " + expectedDim + " vs " + vector.length);
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}
