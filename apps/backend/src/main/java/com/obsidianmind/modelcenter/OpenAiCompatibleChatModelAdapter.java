package com.obsidianmind.modelcenter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidianmind.exception.LlmTimeoutException;
import com.obsidianmind.exception.LlmUnavailableException;
import com.obsidianmind.service.LLMService.LlmStreamListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat 适配器（Phase 5.5）：POST {baseUrl}/chat/completions。
 * DeepSeek Direct / DashScope 兼容模式 / OpenAI Compatible 全部走本实现——
 * 三者协议同构（Authorization: Bearer + stream=true SSE），差异只在 baseUrl / 模型名。
 *
 * 安全约束：
 *   - Authorization 头只在 JVM 内构造，绝不进入日志 / 异常消息；
 *   - 错误映射为语义化错误码（AUTHENTICATION_FAILED / MODEL_NOT_FOUND / …），safeMessage 不含响应体原文
 *     （上游错误体可能回显请求头或账号信息）；
 *   - reasoning_content（思考型模型）安全忽略，绝不混入 answer 流。
 */
public class OpenAiCompatibleChatModelAdapter implements ChatModelAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatModelAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey; // 仅用于构造请求头；equals/hashCode 不暴露
    private final String modelName;
    private final int timeoutSeconds;
    private final String fingerprint;

    public OpenAiCompatibleChatModelAdapter(String baseUrl, String apiKey, String modelName, int timeoutSeconds) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.timeoutSeconds = timeoutSeconds;
        this.fingerprint = (this.baseUrl + "|" + modelName + "|key#"
                + Integer.toHexString(apiKey == null ? 0 : apiKey.hashCode())).intern();
    }

    @Override
    public String complete(String systemPrompt, String userMessage, ChatOptions options) {
        Map<String, Object> body = request(systemPrompt, userMessage, options, false);
        try {
            String resp = client().post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = MAPPER.readTree(resp);
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (LlmUnavailableException | LlmTimeoutException e) {
            throw e;
        } catch (Exception e) {
            throw translate(e, "补全失败");
        }
    }

    @Override
    public void streamComplete(String systemPrompt, String userMessage, ChatOptions options,
                               LlmStreamListener listener) {
        Map<String, Object> body = request(systemPrompt, userMessage, options, true);
        try {
            client().post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (listener.isCancelled()) {
                                    return null; // 客户端断连：停止拉上游流
                                }
                                if (!line.startsWith("data:")) {
                                    continue; // SSE 注释行 / 空行
                                }
                                String payload = line.substring(5).strip();
                                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                                    if ("[DONE]".equals(payload)) {
                                        return null;
                                    }
                                    continue;
                                }
                                JsonNode node = MAPPER.readTree(payload);
                                // delta.content = 正文增量；reasoning_content = 思考内容，安全忽略不转发
                                String content = node.path("choices").path(0).path("delta")
                                        .path("content").asText("");
                                if (!content.isEmpty()) {
                                    listener.onToken(content);
                                }
                            }
                        }
                        return null;
                    });
        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.net.SocketTimeoutException) {
                throw new LlmTimeoutException("LLM 生成超时（" + timeoutSeconds + "s）");
            }
            log.warn("OpenAI 兼容流式调用失败: {}", e.getMessage());
            throw new LlmUnavailableException("LLM 服务不可用");
        } catch (LlmUnavailableException | LlmTimeoutException e) {
            throw e;
        } catch (Exception e) {
            throw translate(e, "流式生成失败");
        }
    }

    @Override
    public String fingerprint() {
        return fingerprint;
    }

    // ---- internals ----

    private Map<String, Object> request(String systemPrompt, String userMessage, ChatOptions options, boolean stream) {
        return Map.of(
                "model", modelName,
                "stream", stream,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)),
                "temperature", options.temperature(),
                "max_tokens", options.maxTokens());
    }

    private RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutSeconds * 1000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(factory)
                .build();
    }

    /** HTTP 状态 → 语义化错误码；消息绝不包含 Key / Authorization / 上游错误体原文。 */
    private LlmUnavailableException translate(Exception e, String action) {
        if (e instanceof HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            return switch (status) {
                case 401, 403 -> new LlmUnavailableException("认证失败：API Key 无效或无权限（AUTHENTICATION_FAILED）");
                case 404 -> new LlmUnavailableException("模型或接口不存在（MODEL_NOT_FOUND）");
                case 429 -> new LlmUnavailableException("请求过于频繁或额度不足（RATE_LIMITED）");
                default -> new LlmUnavailableException(action + "：上游返回 HTTP " + status);
            };
        }
        log.warn("OpenAI 兼容调用失败（{}）: {}", action, e.getClass().getSimpleName());
        return new LlmUnavailableException(action + "：LLM 服务不可用");
    }

    private static String stripTrailingSlash(String url) {
        return url == null ? "" : (url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
    }
}
