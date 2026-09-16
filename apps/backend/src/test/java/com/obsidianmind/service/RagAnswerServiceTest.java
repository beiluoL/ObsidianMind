package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.config.ModelCenterProperties;
import com.obsidianmind.exception.BusinessException;
import com.obsidianmind.exception.OllamaUnavailableException;
import com.obsidianmind.modelcenter.CredentialResolver;
import com.obsidianmind.modelcenter.ModelCenterStorage;
import com.obsidianmind.modelcenter.ModelRouter;
import com.obsidianmind.service.LLMService.LlmStreamListener;
import com.obsidianmind.service.RagAnswerService.RagCompletion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagAnswerService 单元测试：事件顺序、No-Context 拒答、LLM 错误映射、取消、Citation。
 * RetrievalService / LLMService 为 mock；ContextAssembler / RagPromptBuilder 用真实实现。
 */
class RagAnswerServiceTest {

    private RetrievalService retrievalService;
    private LLMService llmService;
    private RagAnswerService service;
    private RecordingSink sink;

    /** 记录全部事件的内存 sink（事件顺序即断言对象）。 */
    private static final class RecordingSink implements RagAnswerService.RagEventSink {
        final List<String> events = new ArrayList<>();
        final StringBuilder content = new StringBuilder();
        RagCompletion completion;
        boolean cancelled;

        @Override
        public void onPhase(String phase) {
            events.add("phase:" + phase);
        }

        @Override
        public void onCitation(ContextAssembler.ContextItem item, int index) {
            events.add("citation:" + item.sourceId());
        }

        @Override
        public void onToken(String token) {
            events.add("message");
            content.append(token);
        }

        @Override
        public void onComplete(RagCompletion c) {
            events.add("done");
            this.completion = c;
        }

        @Override
        public void onError(String code, String message) {
            events.add("error:" + code);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        retrievalService = mock(RetrievalService.class);
        llmService = mock(LLMService.class);
        AiProperties ai = new AiProperties("ollama", null, null, null,
                new AiProperties.Retrieval(5, 20, 0, 2, 200, 512),
                new AiProperties.Rag(6, 12000, 0.1, 1024, false, 120, 180));
        // 空配置存储（临时目录）→ ModelRouter 解析为 legacy Ollama 路径，委托给 mock 的 LLMService
        ModelCenterStorage storage = new ModelCenterStorage(
                new ModelCenterProperties(tempDir.toString()), ai);
        ModelRouter router = new ModelRouter(storage, new CredentialResolver(storage), llmService, ai);
        service = new RagAnswerService(retrievalService, new ContextAssembler(ai),
                new RagPromptBuilder(), router, ai);
        sink = new RecordingSink();
    }

    private RetrievalService.RagRetrievalResult retrievalOf(String content) {
        return new RetrievalService.RagRetrievalResult("HashMap 为什么需要 resize？",
                List.of(new RetrievalService.RagSource("HashMap", "Java/HashMap.md", "# 扩容机制",
                        content, 0.9, "doc-1", 0)), 12);
    }

    @Test
    void shouldEmitEventsInContractOrderAndParseCitations() {
        when(retrievalService.retrieveForRag(anyString(), any()))
                .thenReturn(retrievalOf("HashMap 负载因子默认 0.75，超过则 resize。"));
        doAnswer(inv -> {
            LlmStreamListener listener = inv.getArgument(2);
            listener.onToken("HashMap 扩容是必要的 ");
            listener.onToken("因为负载因子达到 0.75 时会触发 resize [SRC-1]。");
            return null;
        }).when(llmService).streamComplete(anyString(), anyString(), any());

        service.run("HashMap 为什么需要 resize？", null, null, sink);

        // 顺序契约：phase(searching) → citation* → phase(generating) → message* → done
        assertThat(sink.events).startsWith("phase:searching", "citation:SRC-1", "phase:generating");
        assertThat(sink.events).endsWith("done");
        assertThat(sink.completion.content()).contains("resize").contains("[SRC-1]");
        assertThat(sink.completion.citedSourceIds()).containsExactly("SRC-1");
        assertThat(sink.completion.noContext()).isFalse();
        assertThat(sink.completion.sources()).hasSize(1);
        assertThat(sink.completion.sources().get(0).path()).isEqualTo("Java/HashMap.md");
        assertThat(sink.completion.metrics().retrievalMs()).isEqualTo(12);
    }

    @Test
    void shouldRefuseWithoutLlmWhenRetrievalEmpty() {
        when(retrievalService.retrieveForRag(anyString(), any()))
                .thenReturn(new RetrievalService.RagRetrievalResult("q", List.of(), 8));

        service.run("知识库里没有的问题", null, null, sink);

        assertThat(sink.completion.noContext()).isTrue();
        assertThat(sink.completion.content()).contains("知识库中没有找到");
        assertThat(sink.completion.sources()).isEmpty();
        verify(llmService, never()).streamComplete(anyString(), anyString(), any());
        assertThat(sink.events).doesNotContain("phase:generating");
    }

    @Test
    void shouldMapLlmUnavailableToBusinessException() {
        when(retrievalService.retrieveForRag(anyString(), any()))
                .thenReturn(retrievalOf("内容"));
        Mockito.doThrow(new OllamaUnavailableException("connection refused"))
                .when(llmService).streamComplete(anyString(), anyString(), any());

        assertThatThrownBy(() -> service.run("q", null, null, sink))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "LLM_UNAVAILABLE");
        // 错误不产生 done；citation 已发但无终态
        assertThat(sink.events).doesNotContain("done");
    }

    @Test
    void shouldStopEmittingAfterClientDisconnect() {
        when(retrievalService.retrieveForRag(anyString(), any()))
                .thenReturn(retrievalOf("内容"));
        doAnswer(inv -> {
            LlmStreamListener listener = inv.getArgument(2);
            listener.onToken("第一段");
            sink.cancelled = true; // 模拟生成期间客户端断开
            listener.onToken("第二段");
            return null;
        }).when(llmService).streamComplete(anyString(), anyString(), any());

        service.run("q", null, null, sink);

        assertThat(sink.events).doesNotContain("done"); // 断连后不发终态
        assertThat(sink.completion).isNull();
    }

    @Test
    void shouldCapContextWhenTopKLargerThanMaxChunks() {
        // topK=20 请求下只保留前 maxChunks 条（默认 6）
        List<RetrievalService.RagSource> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(new RetrievalService.RagSource("T" + i, "P" + i + ".md", "#h",
                    "chunk-" + i, 0.9 - i * 0.01, "doc-" + i, i));
        }
        when(retrievalService.retrieveForRag(anyString(), anyInt()))
                .thenReturn(new RetrievalService.RagRetrievalResult("q", many, 20));

        service.run("q", 20, null, sink);

        assertThat(sink.completion.sources()).hasSize(6);
        assertThat(sink.completion.metrics().contextChunks()).isEqualTo(6);
        assertThat(sink.completion.metrics().contextTruncated()).isTrue();
    }
}
