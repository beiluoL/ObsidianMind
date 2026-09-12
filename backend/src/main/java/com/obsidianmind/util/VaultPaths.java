package com.obsidianmind.util;

import com.obsidianmind.exception.InvalidRequestException;

import java.nio.file.Path;

/**
 * Vault 相对路径安全工具：拒绝绝对路径、路径穿越（../）、反斜杠与盘符，防止越出 Vault 根目录。
 */
public final class VaultPaths {

    private VaultPaths() {
    }

    /** 校验并归一化 Vault 相对路径（如 "AI/RAG.md"），非法时抛 InvalidRequestException。 */
    public static Path sanitizeRelative(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("路径不能为空");
        }
        if (raw.contains("\\") || raw.contains(":")) {
            throw new InvalidRequestException("非法路径: " + raw);
        }
        Path path = Path.of(raw);
        if (path.isAbsolute() || raw.startsWith("/") || raw.startsWith("~")) {
            throw new InvalidRequestException("非法路径: " + raw);
        }
        Path normalized = path.normalize();
        if (normalized.toString().isEmpty() || normalized.startsWith("..")) {
            throw new InvalidRequestException("非法路径: " + raw);
        }
        return normalized;
    }

    /** 在 Vault 根目录内安全解析路径，最终结果必须仍位于根目录之内。 */
    public static Path resolveSafe(Path vaultRoot, Path relative) {
        Path resolved = vaultRoot.resolve(relative).normalize();
        if (!resolved.startsWith(vaultRoot.normalize())) {
            throw new InvalidRequestException("路径越出 Vault 范围: " + relative);
        }
        return resolved;
    }
}
