package com.obsidianmind.dto.note;

import jakarta.validation.constraints.NotNull;

/**
 * 保存笔记请求：content 为完整 Markdown 原文（Markdown 文件是唯一事实来源）。
 */
public record SaveNoteRequest(@NotNull(message = "content 不能为空") String content) {
}
