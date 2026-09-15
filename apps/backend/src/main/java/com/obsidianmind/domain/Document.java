package com.obsidianmind.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 知识文档模型：一个 Markdown 文件经 Parser 解析后的结构化产物（管线第二环）。
 *
 * 身份约定：id = noteId = Vault 相对路径（项目不变量：Note id = Vault 相对路径）。
 * 单 Vault 场景相对路径全局唯一且与笔记 API 的 id 体系一致，检索结果可直接跳转笔记；
 * 未来多 Vault 时升级为 vaultId:relativePath 复合键（见 docs/architecture/knowledge-pipeline.md）。
 *
 * contentHash 为原文（含 frontmatter）的 SHA-256，是增量索引的判定依据。
 *
 * @param id           文档唯一 id（= Vault 相对路径，禁止绝对路径出后端）
 * @param vaultId      所属 Vault 标识（根路径 SHA-256；多 Vault 检索隔离边界）
 * @param relativePath Vault 相对路径（检索回源用）
 * @param title        frontmatter title → 首个 H1 → 文件名兜底
 * @param content      正文（不含 frontmatter）
 * @param frontmatter  YAML frontmatter 键值
 * @param tags         frontmatter tags + 行内 #tag 合并去重
 * @param headings     全部标题文本（层级顺序）
 * @param wikiLinks    [[WikiLink]] 列表
 * @param modifiedAt   文件最后修改时间（文件系统 mtime；createdAt 未采集，避免拍脑袋字段）
 * @param contentHash  原文 SHA-256（十六进制）
 */
public record Document(
        String id,
        String vaultId,
        String relativePath,
        String title,
        String content,
        Map<String, Object> frontmatter,
        List<String> tags,
        List<String> headings,
        List<String> wikiLinks,
        Instant modifiedAt,
        String contentHash) {
}
