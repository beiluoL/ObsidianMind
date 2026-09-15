package com.obsidianmind.controller;

import com.obsidianmind.dto.chat.ChatRequest;
import com.obsidianmind.dto.chat.ChatResponse;
import com.obsidianmind.service.ChatService;
import com.obsidianmind.service.RagAnswerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat API（Phase 5 RAG）：Query → Retrieval → Context → LLM → SSE → Citation。
 * Controller 只做 Request → Service → Response / SseEmitter；检索、组装、Prompt、流式全在 Service 层。
 */
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "RAG 问答（基于知识库检索 + Ollama 流式生成 + Citation）")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @Operation(summary = "RAG 问答（同步：完整回答 + Sources + 指标）")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        RagAnswerService.RagAnswer answer = chatService.answer(request.message(), request.topK());
        return new ChatResponse(answer.content(), answer.citedSourceIds(),
                answer.sources(), answer.metrics(), answer.noContext());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "RAG 问答（SSE 流式：phase / citation / message / done / error 事件）")
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request.message(), request.topK());
    }
}
