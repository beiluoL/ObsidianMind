package com.obsidianmind.service;

import com.obsidianmind.domain.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 聊天服务（Phase 1）。
 * answer 为 Mock 占位（Phase 2 接入 RetrievalService → LLMService 真实 RAG 链路），
 * 但 relatedNotes 来自真实全文检索——不伪造检索结果。
 * API 结构为未来 RAG 预留：{answer, sources[], relatedNotes[]}。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int RELATED_LIMIT = 3;

    private final SearchService searchService;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "chat-sse");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${spring.application.name:ObsidianMind}")
    private String applicationName;

    public ChatService(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 生成回答（Phase 1 Mock）：answer 为结构占位文本，relatedNotes 取真实全文检索 Top3——
     * 检索结果是真实的，只有 LLM 生成部分是 Mock（不伪造检索）。
     *
     * @param message 用户问题（同时作为全文检索的 query）
     * @param scope   检索范围（Phase 1 仅记录日志，未启用范围过滤）
     * @return answer + sources（恒为空，RAG 接入后填充）+ relatedNotes
     */
    public ChatAnswer answer(String message, String scope) {
        log.info("Chat 请求: scope={}, message 长度={}", scope, message.length());
        List<SearchResult> related = searchService.search(message);
        String relatedNotesText = related.stream()
                .limit(RELATED_LIMIT)
                .map(r -> "「" + r.title() + "」")
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
        String answer = """
                【%s Phase 1 · Mock 回答】

                已收到你的问题：「%s」。

                当前阶段（Phase 1）Chat 为结构预留 + Mock 回答，LLM 生成链路将在 Phase 2 接入：
                问题 → 向量/全文混合检索 → 上下文拼装 → Ollama 生成 → SSE 流式返回。

                %s
                """.formatted(
                applicationName,
                message,
                relatedNotesText.isEmpty()
                        ? "全文检索未在你的 Vault 中找到与该问题直接相关的笔记。"
                        : "全文检索在你的 Vault 中找到相关笔记：" + relatedNotesText + "（见下方 relatedNotes）。");
        return new ChatAnswer(answer, List.of(), related.stream().limit(RELATED_LIMIT).toList());
    }

    /** SSE 流式输出：先发 phase 事件，再按 token 流式发 message，最后发 done。 */
    public SseEmitter stream(String message, String scope) {
        SseEmitter emitter = new SseEmitter(60_000L);
        sseExecutor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().name("phase").data("{\"phase\":\"searching\"}"));
                ChatAnswer chatAnswer = answer(message, scope);
                emitter.send(SseEmitter.event().name("phase").data("{\"phase\":\"generating\"}"));

                for (String token : chatAnswer.answer().split("(?<=\\n)|(?<=。)|(?<=\\s)")) {
                    emitter.send(SseEmitter.event().name("message").data(token));
                    Thread.sleep(30);
                }

                emitter.send(SseEmitter.event().name("done").data(toJson(chatAnswer)));
                emitter.complete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(e);
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** 手工拼接 done 事件的 JSON（仅转义反斜杠与双引号；relatedNotes 数量有限且字段受控）。 */
    private String toJson(ChatAnswer chatAnswer) {
        StringBuilder related = new StringBuilder("[");
        for (int i = 0; i < chatAnswer.relatedNotes().size(); i++) {
            SearchResult note = chatAnswer.relatedNotes().get(i);
            if (i > 0) {
                related.append(",");
            }
            related.append("{\"noteId\":\"").append(escape(note.noteId()))
                    .append("\",\"title\":\"").append(escape(note.title()))
                    .append("\",\"score\":").append(note.score()).append("}");
        }
        related.append("]");
        return "{\"answer\":\"\",\"sources\":[],\"relatedNotes\":" + related + "}";
    }

    private String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 聊天回答：answer + sources + relatedNotes（未来 RAG 契约）。
     */
    public record ChatAnswer(String answer, List<SearchResult> sources, List<SearchResult> relatedNotes) {
    }
}
