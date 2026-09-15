package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.exception.BusinessException;
import com.obsidianmind.exception.LlmTimeoutException;
import com.obsidianmind.exception.LlmUnavailableException;
import com.obsidianmind.exception.OllamaUnavailableException;
import com.obsidianmind.exception.RagPipelineException;
import com.obsidianmind.service.LLMService.LlmStreamListener;
import com.obsidianmind.util.CitationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 回答编排服务（Phase 5）：Query → Retrieval → Context → Citation → Prompt → LLM 流 → Complete。
 *
 * 只做编排：Milvus SDK / Markdown 解析 / Chunking / Prompt 字符串巨型拼接都不在这里
 * （分别在 VectorRepository / Parser / Chunker / RagPromptBuilder）。它不依赖 Servlet——
 * SSE 由 ChatService 通过 RagEventSink 适配，单元测试用内存 sink 即可驱动全链路。
 *
 * 错误模型（SSE 场景由 ChatService 把 BusinessException 转成 error 事件，不吞不包装）：
 *   检索：OLLAMA_UNAVAILABLE / MILVUS_UNAVAILABLE / VECTOR_STORE_ERROR / INVALID_REQUEST（RetrievalService 抛出，原样传播）
 *   上下文：CONTEXT_BUILD_ERROR（组装器意外失败）
 *   生成：LLM_UNAVAILABLE / LLM_TIMEOUT / LLM_STREAM_ERROR
 *
 * No-Context（检索为空）：不问 LLM、不硬答，直接返回系统生成的拒答文案——
 * 「知识库中没有足够相关内容」由系统掌控，而不是寄希望于模型自觉。
 */
@Service
public class RagAnswerService {

    private static final Logger log = LoggerFactory.getLogger(RagAnswerService.class);

    /** 检索为空时的系统拒答文案（不经 LLM，杜绝无来源硬答）。 */
    static final String NO_CONTEXT_ANSWER =
            "知识库中没有找到与该问题足够相关的内容，无法基于你的笔记回答。"
                    + "可以先运行「知识索引」，或换个更接近笔记措辞的问法。";

    private final RetrievalService retrievalService;
    private final ContextAssembler contextAssembler;
    private final RagPromptBuilder promptBuilder;
    private final LLMService llmService;
    private final AiProperties aiProperties;

    public RagAnswerService(RetrievalService retrievalService,
                            ContextAssembler contextAssembler,
                            RagPromptBuilder promptBuilder,
                            LLMService llmService,
                            AiProperties aiProperties) {
        this.retrievalService = retrievalService;
        this.contextAssembler = contextAssembler;
        this.promptBuilder = promptBuilder;
        this.llmService = llmService;
        this.aiProperties = aiProperties;
    }

    /**
     * 编排一次完整 RAG 回答（同步与流式共用）。
     * 事件顺序契约：phase(searching) → [citation]* → phase(generating) → [message]* → done；error 可发生在任何阶段之后并终止流程。
     *
     * @throws BusinessException 语义化错误码（INVALID_REQUEST / OLLAMA_UNAVAILABLE / MILVUS_UNAVAILABLE /
     *                         VECTOR_STORE_ERROR / CONTEXT_BUILD_ERROR / LLM_UNAVAILABLE / LLM_TIMEOUT / LLM_STREAM_ERROR）
     */
    public void run(String rawQuery, Integer requestedTopK, RagEventSink sink) {
        long startedAt = System.currentTimeMillis();

        // ① 检索（异常原样传播，code 已语义化）
        sink.onPhase("searching");
        long retrievalMs;
        RetrievalService.RagRetrievalResult retrieval;
        try {
            retrieval = retrievalService.retrieveForRag(rawQuery, requestedTopK);
        } catch (BusinessException e) {
            throw e; // INVALID_REQUEST / OLLAMA_UNAVAILABLE / MILVUS_UNAVAILABLE / VECTOR_STORE_ERROR …语义化码原样上抛
        } catch (RuntimeException e) {
            log.error("检索阶段未预期失败", e);
            throw new RagPipelineException("RETRIEVAL_ERROR", "知识检索失败");
        }
        retrievalMs = retrieval.elapsedMs();
        if (sink.isCancelled()) {
            return; // 客户端已断开：不再做任何后续工作
        }

        // ② Context 组装（受控、有上限；失败不能伪装成"无上下文"）
        long contextStartedAt = System.currentTimeMillis();
        ContextAssembler.RagContext context;
        try {
            context = contextAssembler.assemble(retrieval);
        } catch (RuntimeException e) {
            log.error("Context 组装失败", e);
            throw new RagPipelineException("CONTEXT_BUILD_ERROR", "上下文组装失败");
        }
        long contextMs = System.currentTimeMillis() - contextStartedAt;

        AiProperties.Rag ragCfg = aiProperties.ragOrDefault();
        log.info("RAG 链路: chunks={} contextChars={} truncated={} retrievalMs={} contextMs={}",
                context.items().size(), context.contextChars(), context.truncated(), retrievalMs, contextMs);

        // ③ No-Context：检索为空 → 系统拒答（不经 LLM）
        if (context.isEmpty()) {
            log.info("RAG 无上下文，走系统拒答路径: queryLen={}", rawQuery.length());
            sink.onComplete(new RagCompletion(NO_CONTEXT_ANSWER, List.of(), List.of(),
                    RagMetrics.of(retrievalMs, contextMs, -1, 0, System.currentTimeMillis() - startedAt,
                            0, 0, 0, false), true));
            return;
        }

        // ④ Source Registry 下发（先于 token：前端可边流式渲染边显示可点击引用）
        List<CitationView> citations = context.items().stream()
                .map(item -> CitationView.of(item, aiProperties.retrievalOrDefault().snippetLength()))
                .toList();
        for (int i = 0; i < context.items().size(); i++) {
            sink.onCitation(context.items().get(i), i + 1);
        }

        // ⑤ Prompt 组装 + LLM 流式生成
        sink.onPhase("generating");
        RagPromptBuilder.Prompt prompt = promptBuilder.build(context);
        StringBuilder content = new StringBuilder();
        long[] firstTokenAt = {-1};
        try {
            llmService.streamComplete(prompt.systemPrompt(), prompt.userMessage(), new LlmStreamListener() {
                @Override
                public void onToken(String token) {
                    if (firstTokenAt[0] < 0) {
                        firstTokenAt[0] = System.currentTimeMillis();
                    }
                    content.append(token);
                    sink.onToken(token);
                }

                @Override
                public boolean isCancelled() {
                    return sink.isCancelled();
                }
            });
        } catch (OllamaUnavailableException e) {
            throw new LlmUnavailableException("LLM 不可用: " + e.getMessage());
        } catch (LlmTimeoutException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("LLM 流式生成失败", e);
            throw new RagPipelineException("LLM_STREAM_ERROR", "LLM 生成中断");
        }
        if (sink.isCancelled()) {
            log.info("客户端在生成期间断开，RAG 编排终止: queryLen={}", rawQuery.length());
            return;
        }

        // ⑥ Citation 解析：来源由系统 Registry 校验；模型编造的编号只记录不报错
        Set<String> knownIds = context.items().stream()
                .map(ContextAssembler.ContextItem::sourceId)
                .collect(Collectors.toSet());
        CitationParser.Result citationsInAnswer = CitationParser.parse(content.toString(), knownIds);
        if (!citationsInAnswer.unknown().isEmpty()) {
            log.warn("LLM 引用了不存在的 Source 编号（仅记录）: {}", citationsInAnswer.unknown());
        }

        long llmMs = firstTokenAt[0] > 0 ? System.currentTimeMillis() - firstTokenAt[0] : -1;
        RagMetrics metrics = RagMetrics.of(retrievalMs, contextMs,
                firstTokenAt[0] > 0 ? firstTokenAt[0] - startedAt : -1, llmMs,
                System.currentTimeMillis() - startedAt,
                context.items().size(), context.contextChars(), prompt.promptChars(), context.truncated());
        sink.onComplete(new RagCompletion(content.toString(), citationsInAnswer.cited(), citations, metrics, false));
    }

    /**
     * 同步回答（非流式端点复用同一编排）：收集 sink 结果；BusinessException 原样抛出由
     * GlobalExceptionHandler 映射 HTTP 状态。
     */
    public RagAnswer answerSync(String rawQuery, Integer requestedTopK) {
        StringBuilder content = new StringBuilder();
        List<CitationView>[] citationsHolder = new List[]{List.of()};
        CitationParser.Result[] citedHolder = new CitationParser.Result[]{CitationParser.Result.empty()};
        RagCompletion[] completionHolder = new RagCompletion[null == null ? 1 : 1];
        run(rawQuery, requestedTopK, new RagEventSink() {
            @Override
            public void onPhase(String phase) {
                // 同步模式不关心阶段
            }

            @Override
            public void onCitation(ContextAssembler.ContextItem item, int index) {
                // 引用在 onComplete 的 citations 中统一返回
            }

            @Override
            public void onToken(String token) {
                content.append(token);
            }

            @Override
            public void onComplete(RagCompletion completion) {
                completionHolder[0] = completion;
            }

            @Override
            public void onError(String code, String message) {
                throw new IllegalStateException(code); // 不会发生：run() 以异常表达错误
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        });
        RagCompletion completion = completionHolder[0];
        return new RagAnswer(completion.content(), completion.citedSourceIds(),
                completion.sources(), completion.metrics(), completion.noContext());
    }

    /** SSE 事件出口抽象：RagAnswerService 不依赖 Servlet / SseEmitter；测试用内存实现。 */
    public interface RagEventSink {

        void onPhase(String phase);

        void onCitation(ContextAssembler.ContextItem item, int index);

        void onToken(String token);

        void onComplete(RagCompletion completion);

        void onError(String code, String message);

        /** 客户端断连轮询：true 后编排应尽快终止且不再发事件。 */
        boolean isCancelled();
    }

    /** 面向 UI 的引用视图：只含展示字段，绝不含完整 Chunk 内容（Context 仅供 LLM）。 */
    public record CitationView(
            int index,
            String sourceId,
            String title,
            String path,
            String heading,
            String snippet,
            double score) {

        static CitationView of(ContextAssembler.ContextItem item, int snippetLength) {
            return new CitationView(
                    Integer.parseInt(item.sourceId().substring("SRC-".length())),
                    item.sourceId(),
                    item.title(),
                    item.path(),
                    item.heading(),
                    fold(item.content(), snippetLength),
                    item.score());
        }

        /** 码点安全折叠空白（与 RetrievalService.snippet 同策略；此处独立实现避免把 UI 工具反向耦合进检索层）。 */
        private static String fold(String content, int maxLength) {
            String flat = content == null ? "" : content.strip().replaceAll("\\s+", " ");
            flat = flat.replaceFirst("^#{1,6}\\s+", "");
            if (maxLength <= 0 || flat.codePointCount(0, flat.length()) <= maxLength) {
                return flat;
            }
            int end = flat.offsetByCodePoints(0, maxLength);
            return flat.substring(0, end) + "…";
        }
    }

    /** 性能指标：只记数字与长度，不记完整 Prompt / 笔记内容。 */
    public record RagMetrics(
            long retrievalMs,
            long contextMs,
            long firstTokenMs,
            long llmMs,
            long totalMs,
            int contextChunks,
            int contextChars,
            int promptChars,
            boolean contextTruncated) {

        public static RagMetrics of(long retrievalMs, long contextMs, long firstTokenMs, long llmMs, long totalMs,
                                    int contextChunks, int contextChars, int promptChars, boolean truncated) {
            return new RagMetrics(retrievalMs, contextMs, firstTokenMs, llmMs, totalMs,
                    contextChunks, contextChars, promptChars, truncated);
        }
    }

    /** done 事件负载：content + 系统校验后的引用 + Registry 快照 + 指标。 */
    public record RagCompletion(
            String content,
            List<String> citedSourceIds,
            List<CitationView> sources,
            RagMetrics metrics,
            boolean noContext) {
    }

    /** 同步回答结果（POST /api/v1/chat）。 */
    public record RagAnswer(
            String content,
            List<String> citedSourceIds,
            List<CitationView> sources,
            RagMetrics metrics,
            boolean noContext) {
    }
}
