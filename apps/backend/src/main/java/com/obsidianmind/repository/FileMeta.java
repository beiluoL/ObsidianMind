package com.obsidianmind.repository;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 扫描阶段收集的文件元数据（不含正文，正文按需读取）。
 */
public record FileMeta(
        String path,
        String name,
        long size,
        Instant modifiedAt
) {

    public static FileMeta of(Path relativePath, Path absolutePath) {
        try {
            var attrs = java.nio.file.Files.readAttributes(absolutePath, java.nio.file.attribute.BasicFileAttributes.class);
            return new FileMeta(
                    relativePath.toString(),
                    relativePath.getFileName().toString(),
                    attrs.size(),
                    attrs.lastModifiedTime().toInstant());
        } catch (java.io.IOException e) {
            throw new com.obsidianmind.exception.NoteReadFailedException("读取文件元数据失败: " + relativePath);
        }
    }
}
