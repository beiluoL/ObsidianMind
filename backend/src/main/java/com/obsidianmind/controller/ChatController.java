package com.obsidianmind.controller;

import com.obsidianmind.dto.chat.ChatRequest;
import com.obsidianmind.dto.chat.ChatResponse;
import com.obsidianmind.service.ChatService;
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
 * Chat API：Phase 1 结构预留 + Mock 回答 + SSE 流式骨架。
 * Phase 2 替换为 RetrievalService → LLMService 真实 RAG 链路，API 契约不变。
 */
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "AI 问答（SSE 流式）")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @Operation(summary = "问答（Phase 1 Mock，结构为 RAG 预留）")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        ChatService.ChatAnswer answer = chatService.answer(request.message(), request.scopeOrDefault());
        return new ChatResponse(answer.answer(), answer.sources(), answer.relatedNotes());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式问答（SSE：phase / message / done 事件）")
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request.message(), request.scopeOrDefault());
    }
}
