package com.obsidianmind.domain;

/**
 * 知识切块模型：Document 经 Chunker 切出的检索单元（管线第三环）。
 * id 形如 {documentId}#c{n}：确定性生成，同一文档重切块后可对齐替换。
 *
 * @param id           Chunk 唯一 id（Milvus 主键）
 * @param vaultId      所属 Vault 标识（随向量冗余存储，检索过滤与删除清理的隔离边界）
 * @param documentId   所属文档 id（= Vault 相对路径）
 * @param relativePath Vault 相对路径（冗余存储，检索结果直接回源）
 * @param title        文档标题
 * @param headingPath  标题路径（如 "JVM / 内存区域 / Heap"），Sources 定位核心字段
 * @param chunkIndex   文档内序号（0 起）
 * @param startOffset  在 Document 正文中的起始字符偏移（含）
 * @param endOffset    在 Document 正文中的结束字符偏移（不含）
 * @param content      切块正文
 * @param contentHash  所属文档的 contentHash（同文档所有 Chunk 相同，增量索引用）
 */
public record Chunk(
        String id,
        String vaultId,
        String documentId,
        String relativePath,
        String title,
        String headingPath,
        int chunkIndex,
        int startOffset,
        int endOffset,
        String content,
        String contentHash) {
}
