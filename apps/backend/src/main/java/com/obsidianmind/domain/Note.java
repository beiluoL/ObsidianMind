package com.obsidianmind.domain;

import java.time.Instant;
import java.util.List;

/**
 * 一篇 Markdown 笔记。id 即 Vault 相对路径（与前端契约一致）。
 */
public record Note(
        String id,
        String title,
        String path,
        String folder,
        String content,
        List<String> tags,
        List<String> links,
        Instant modifiedAt,
        int wordCount
) {
}
