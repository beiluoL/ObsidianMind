package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Context 组装器（Phase 5）：RetrievalResult → 受控知识上下文（RagContext）。
 *
 * 设计核心：检索结果 ≠ Prompt Context。Retrieval 是「向量空间里最相似的 K 条」，
 * Context 是「准备交给 LLM 的、有数量与字符上限的受控资料区」。必须存在这个中间层，
 * 禁止把检索结果 toString 后直接塞进 Prompt。
 *
 * 数量控制（不可协商）：
 * - maxChunks（ai.rag.max-chunks）：只保留相似度排名最高的前 N 条——TopK 再大也不全部进入上下文；
 * - maxChars（ai.rag.max-chars）：总字符超限时按排名整体停止（保留高相关，不静默无限截断单条）；
 * - 截断发生时置 truncated=true，由上层记录日志与指标，绝不悄悄丢内容。
 *
 * Source ID（SRC-1、SRC-2…）由本层按相似度排名生成，全局稳定：
 * LLM 只被允许「引用」这些编号，绝不自己编造 Source / 路径（Citation 由系统 Registry 掌控）。
 */
@Service
public class ContextAssembler {

    private final AiProperties aiProperties;

    public ContextAssembler(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    /**
     * 将检索结果裁剪为受控上下文。
     *
     * @param retrieval retrieveForRag 的结果（完整 Chunk 内容）
     * @return RagContext（items 可能为空 = 无可用上下文，上层必须走 No-Context 拒答路径）
     */
    public RagContext assemble(RetrievalService.RagRetrievalResult retrieval) {
        AiProperties.Rag cfg = aiProperties.ragOrDefault();
        List<ContextItem> items = new ArrayList<>(Math.min(cfg.maxChunks(), retrieval.results().size()));
        int totalChars = 0;
        boolean truncated = false;

        for (RetrievalService.RagSource source : retrieval.results()) {
            if (items.size() >= cfg.maxChunks()) {
                truncated = true; // 排名在后的整体舍弃，不是静默吞掉
                break;
            }
            int contentChars = source.content().length();
            if (totalChars + contentChars > cfg.maxChars()) {
                // 放不下就整体停止：宁可少一条，也不塞半条污染语义（保留高相关 = 排名靠前的先入）
                truncated = true;
                break;
            }
            items.add(new ContextItem(
                    "SRC-" + (items.size() + 1),
                    source.title(),
                    source.path(),
                    source.heading(),
                    source.content(),
                    source.documentId(),
                    source.chunkIndex(),
                    source.score()));
            totalChars += contentChars;
        }
        return new RagContext(retrieval.query(), List.copyOf(items), totalChars, truncated);
    }

    /** 一条进入 Prompt 的上下文：sourceId 为系统生成的稳定编号（SRC-n），content 为完整 Chunk 内容。 */
    public record ContextItem(
            String sourceId,
            String title,
            String path,
            String heading,
            String content,
            String documentId,
            int chunkIndex,
            double score) {
    }

    /** 受控知识上下文：query + 编号化条目 + 审计指标（chars / truncated）。 */
    public record RagContext(String query, List<ContextItem> items, int contextChars, boolean truncated) {

        public boolean isEmpty() {
            return items.isEmpty();
        }
    }
}
