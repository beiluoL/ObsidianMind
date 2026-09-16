package com.obsidianmind.parser;

import com.obsidianmind.domain.Document;
import com.obsidianmind.repository.FileMeta;

import java.util.List;

/**
 * Document 工厂（Phase 6 从 KnowledgeIndexService.buildDocument 提取）：
 * ParsedMarkdown + 文件元数据 → 领域 Document。
 *
 * 提取原因：知识索引（KnowledgeIndexService）与关键词检索索引（VaultKeywordIndex）需要
 * 完全相同的解析语义——同一篇笔记在向量链路和 BM25 链路里必须切出相同的 Chunk，
 * 否则两路候选无法按 chunkId 对齐融合。纯函数、无状态。
 */
public final class DocumentFactory {

    private DocumentFactory() {
    }

    /**
     * 由解析结果构建领域 Document（字段语义与 KnowledgeIndexService 原实现一致）。
     *
     * @param vaultId Vault 标识（根路径 SHA-256）
     * @param meta    扫描元数据（path/name/modifiedAt）
     * @param parsed  Markdown 解析结果
     * @param hash    原文 SHA-256
     */
    public static Document create(String vaultId, FileMeta meta, ParsedMarkdown parsed, String hash) {
        String title = parsed.title() != null ? parsed.title()
                : meta.name().replaceAll("(?i)\\.md$", "");
        List<String> headings = parsed.body().lines()
                .filter(line -> line.matches("^#{1,6}\\s+.*"))
                .map(line -> line.replaceFirst("^#+\\s+", "").trim())
                .toList();
        return new Document(
                meta.path(),
                vaultId,
                meta.path(),
                title,
                parsed.body(),
                parsed.frontmatter(),
                parsed.tags(),
                headings,
                parsed.wikiLinks(),
                meta.modifiedAt(),
                hash);
    }
}
