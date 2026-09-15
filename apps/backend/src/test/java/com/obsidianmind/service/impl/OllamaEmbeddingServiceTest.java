package com.obsidianmind.service.impl;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.exception.EmbeddingException;
import com.obsidianmind.exception.OllamaUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OllamaEmbeddingService 契约测试（Mock HTTP，不依赖真实 Ollama）。
 * 覆盖：成功批量 / 数量不符 / 维度不一致 / 服务不可用重试后失败 / 模型错误。
 */
class OllamaEmbeddingServiceTest {

    private MockRestServiceServer server;
    private OllamaEmbeddingService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:11434");
        server = MockRestServiceServer.bindTo(builder).build();
        AiProperties ai = new AiProperties("ollama",
                new AiProperties.Ollama("http://localhost:11434", "chat", "test-embed", 3),
                new AiProperties.Chunk(800, 100),
                new AiProperties.Embedding(16, 2),
                new AiProperties.Retrieval(5, 20, 0, 2, 200, 512), null);
        service = new OllamaEmbeddingService(ai, builder.build());
    }

    private String embeddingsJson(double[][] vectors) {
        StringBuilder sb = new StringBuilder("{\"embeddings\":[");
        for (int i = 0; i < vectors.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int j = 0; j < vectors[i].length; j++) {
                if (j > 0) {
                    sb.append(',');
                }
                sb.append(vectors[i][j]);
            }
            sb.append(']');
        }
        return sb.append("]}").toString();
    }

    @Test
    void batchEmbedReturnsVectorsInInputOrder() {
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(embeddingsJson(new double[][]{{1, 0}, {0, 1}}), MediaType.APPLICATION_JSON));

        List<float[]> vectors = service.embed(List.of("甲", "乙"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)[0]).isEqualTo(1.0f);
        assertThat(vectors.get(1)[1]).isEqualTo(1.0f);
        server.verify();
    }

    @Test
    void countMismatchFails() {
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andRespond(withSuccess(embeddingsJson(new double[][]{{1, 0}}), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.embed(List.of("甲", "乙")))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("数量不符");
    }

    @Test
    void dimensionInconsistencyFails() {
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andRespond(withSuccess("{\"embeddings\":[[1,0],[1,0,0]]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.embed(List.of("甲", "乙")))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("维度不一致");
    }

    @Test
    void emptyVectorFails() {
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andRespond(withSuccess("{\"embeddings\":[[]]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.embed(List.of("甲")))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("空向量");
    }

    @Test
    void connectionFailureRetriesOnceThenThrowsUnavailable() {
        server.expect(requestTo("http://localhost:11434/api/embed")).andRespond(withException(new java.io.IOException("refused")));
        server.expect(requestTo("http://localhost:11434/api/embed")).andRespond(withServerError());

        assertThatThrownBy(() -> service.embed(List.of("甲")))
                .isInstanceOf(OllamaUnavailableException.class);
        server.verify(); // 重试恰好 1 次：共 2 次请求
    }

    @Test
    void modelErrorResponseFailsWithHint() {
        server.expect(requestTo("http://localhost:11434/api/embed"))
                .andRespond(withSuccess("{\"error\":\"model not found\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.embed(List.of("甲")))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("model not found");
    }

    @Test
    void emptyInputShortCircuits() {
        assertThat(service.embed(List.of())).isEmpty();
    }
}
