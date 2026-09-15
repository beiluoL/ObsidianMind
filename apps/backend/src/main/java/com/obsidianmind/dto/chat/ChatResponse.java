package com.obsidianmind.dto.chat;

import com.obsidianmind.service.RagAnswerService.CitationView;
import com.obsidianmind.service.RagAnswerService.RagMetrics;

import java.util.List;

/**
 * 聊天响应（Phase 5 RAG，POST /api/v1/chat 同步端点）：
 * content = 回答正文（含 [SRC-n] 引用标记）；
 * citedSourceIds = 系统从回答中解析并经 Source Registry 校验的引用；
 * sources = 本次回答的 Source Registry 快照（index 与正文 [SRC-n] 数字一致）；
 * noContext = true 表示检索无结果，content 为系统拒答文案（未经 LLM）。
 */
public record ChatResponse(
        String content,
        List<String> citedSourceIds,
        List<CitationView> sources,
        RagMetrics metrics,
        boolean noContext) {
}
