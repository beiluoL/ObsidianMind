package com.obsidianmind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidianmind.config.AiProperties;
import com.obsidianmind.exception.BusinessException;
import com.obsidianmind.service.RagAnswerService.CitationView;
import com.obsidianmind.service.RagAnswerService.RagCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat 服务（Phase 5 RAG）：RagAnswerService 编排 + SSE 事件适配。
 *
 * 事件契约（text/event-stream，data 均为 JSON）：
 *   phase     {"phase":"searching"|"generating"}
 *   citation  {"index":1,"sourceId":"SRC-1","title":...,"path":...,"heading":...,"snippet":...,"score":...}
 *   message   {"content":"增量 token"}
 *   done      {"content":全文,"citedSourceIds":[...],"sources":[...],"metrics":{...},"noContext":false}
 *   error     {"code":"LLM_UNAVAILABLE","message":"..."}
 *
 * 生命周期：SseEmitter 超时 = ai.rag.stream-timeout-seconds（显式设置，不无限挂起）；
 * 客户端断连（onError / onTimeout / 发送失败）置 cancelled → RagAnswerService 终止编排、
 * LlmStreamListener 停止拉取上游 Ollama 流（连接关闭后 Ollama 中止生成）。
 * 事件数据统一经 Jackson 序列化（record → JSON），不手工拼字符串。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RagAnswerService ragAnswerService;
    private final AiProperties aiProperties;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "rag-sse");
        thread.setDaemon(true);
        return thread;
    });

    public ChatService(RagAnswerService ragAnswerService, AiProperties aiProperties) {
        this.ragAnswerService = ragAnswerService;
        this.aiProperties = aiProperties;
    }

    /** 同步问答：与流式共用同一编排（POST /api/v1/chat）。 */
    public RagAnswerService.RagAnswer answer(String message, Integer topK, String modelId) {
        return ragAnswerService.answerSync(message, topK, modelId);
    }

    /** SSE 流式问答（POST /api/v1/chat/stream）。 */
    public SseEmitter stream(String message, Integer topK, String modelId) {
        long timeoutMs = aiProperties.ragOrDefault().streamTimeoutSeconds() * 1000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onTimeout(() -> {
            cancelled.set(true);
            log.info("SSE 超时终止（{}ms）", timeoutMs);
        });
        emitter.onError(e -> {
            cancelled.set(true);
            log.info("SSE 连接错误（客户端断连）: {}", e.getMessage());
        });

        sseExecutor.execute(() -> {
            SseEventSink sink = new SseEventSink(emitter, cancelled);
            try {
                ragAnswerService.run(message, topK, modelId, sink);
                emitter.complete();
            } catch (BusinessException e) {
                sink.onError(e.getCode(), e.getMessage());
                emitter.complete();
            } catch (RuntimeException e) {
                log.error("RAG 流式编排未预期失败", e);
                sink.onError("INTERNAL_ERROR", "服务内部错误");
                emitter.complete();
            }
        });
        return emitter;
    }

    /** RagEventSink → SseEmitter 适配：发送失败即置 cancelled（连接已死，停止一切后续事件与上游 LLM 拉流）。 */
    private final class SseEventSink implements RagAnswerService.RagEventSink {

        private final SseEmitter emitter;
        private final AtomicBoolean cancelled;

        private SseEventSink(SseEmitter emitter, AtomicBoolean cancelled) {
            this.emitter = emitter;
            this.cancelled = cancelled;
        }

        @Override
        public void onPhase(String phase) {
            send("phase", OBJECT_MAPPER.createObjectNode().put("phase", phase));
        }

        @Override
        public void onCitation(ContextAssembler.ContextItem item, int index) {
            // CitationView 形状与 done.sources 完全一致：前端只解析一种 Source 结构
            send("citation", CitationView.of(item, aiProperties.retrievalOrDefault().snippetLength()));
        }

        @Override
        public void onToken(String token) {
            send("message", OBJECT_MAPPER.createObjectNode().put("content", token));
        }

        @Override
        public void onComplete(RagCompletion completion) {
            send("done", completion);
        }

        @Override
        public void onError(String code, String message) {
            var node = OBJECT_MAPPER.createObjectNode();
            node.put("code", code);
            node.put("message", message);
            send("error", node);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        private void send(String eventName, Object payload) {
            if (cancelled.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                cancelled.set(true);
                log.info("SSE 发送失败，标记断连: {}", e.getMessage());
            }
        }
    }
}
