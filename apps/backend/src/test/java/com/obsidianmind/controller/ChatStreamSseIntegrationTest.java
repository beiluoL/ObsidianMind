package com.obsidianmind.controller;

import com.obsidianmind.service.ContextAssembler;
import com.obsidianmind.service.RagAnswerService;
import com.obsidianmind.service.RagAnswerService.CitationView;
import com.obsidianmind.service.RagAnswerService.RagCompletion;
import com.obsidianmind.service.RagAnswerService.RagMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat SSE 契约测试：用 mock 的 RagAnswerService 驱动事件序列，
 * 验证 Controller + ChatService 的 SSE 事件模型与顺序（phase → citation → message → done）。
 * RagAnswerService 的业务编排行为由 RagAnswerServiceTest 覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatStreamSseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagAnswerService ragAnswerService;

    @Test
    void shouldEmitSseEventsInContractOrder() throws Exception {
        doAnswer(inv -> {
            RagAnswerService.RagEventSink sink = inv.getArgument(3);
            sink.onPhase("searching");
            sink.onCitation(new ContextAssembler.ContextItem(
                    "SRC-1", "HashMap", "Java/HashMap.md", "# 扩容机制", "完整内容", "doc-1", 0, 0.9), 1);
            sink.onPhase("generating");
            sink.onToken("HashMap ");
            sink.onToken("扩容…");
            sink.onComplete(new RagCompletion("HashMap 扩容… [SRC-1]", List.of("SRC-1"),
                    List.of(new CitationView(1, "SRC-1", "HashMap", "Java/HashMap.md", "# 扩容机制",
                            "完整内容", 0.9)),
                    new RagMetrics(10, 1, 20, 500, 600, 1, 4, 900, false), false));
            return null;
        }).when(ragAnswerService).run(anyString(), any(), any(), any());

        MvcResult result = mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"HashMap 为什么需要 resize？\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:phase")))
                .andExpect(content().string(containsString("event:citation")))
                .andExpect(content().string(containsString("event:message")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("\"sourceId\":\"SRC-1\"")))
                .andExpect(content().string(containsString("\"noContext\":false")));
    }

    @Test
    void shouldEmitErrorEventWhenOrchestrationFails() throws Exception {
        doThrow(new com.obsidianmind.exception.LlmUnavailableException("LLM 不可用"))
                .when(ragAnswerService).run(anyString(), any(), any(), any());

        MvcResult result = mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"q\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("LLM_UNAVAILABLE")));
    }

    @Test
    void shouldReturn400ForBlankMessage() throws Exception {
        mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldReturn400ForOverlongMessage() throws Exception {
        mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + "长".repeat(600) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
