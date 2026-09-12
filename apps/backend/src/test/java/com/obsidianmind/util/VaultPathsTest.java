package com.obsidianmind.util;

import com.obsidianmind.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路径安全单元测试：路径穿越 / 绝对路径 / 越界解析。
 */
class VaultPathsTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldAcceptNormalRelativePath() {
        assertEquals("AI/RAG.md", VaultPaths.sanitizeRelative("AI/RAG.md").toString());
        assertEquals("note.md", VaultPaths.sanitizeRelative("./note.md").toString());
    }

    @Test
    void shouldRejectPathTraversal() {
        assertThrows(InvalidRequestException.class, () -> VaultPaths.sanitizeRelative("../etc/passwd"));
        assertThrows(InvalidRequestException.class, () -> VaultPaths.sanitizeRelative("AI/../../etc/passwd"));
        assertThrows(InvalidRequestException.class, () -> VaultPaths.sanitizeRelative(".."));
    }

    @Test
    void shouldRejectAbsoluteAndBackslashPath() {
        assertThrows(InvalidRequestException.class, () -> VaultPaths.sanitizeRelative("/etc/passwd"));
        assertThrows(InvalidRequestException.class, () -> VaultPaths.sanitizeRelative("AI\\RAG.md"));
        assertThrows(InvalidRequestException.class, () -> VaultPaths.sanitizeRelative("C:Users/evil.md"));
        assertThrows(InvalidRequestException.class, () -> VaultPaths.sanitizeRelative("  "));
        assertThrows(InvalidRequestException.class, () -> VaultPaths.sanitizeRelative(null));
    }

    @Test
    void shouldResolveSafelyInsideVault() {
        Path relative = VaultPaths.sanitizeRelative("AI/RAG.md");
        Path resolved = VaultPaths.resolveSafe(tempDir, relative);
        assertTrue(resolved.startsWith(tempDir));
    }

    @Test
    void shouldRejectResolutionOutsideVault() {
        // normalize 后仍是相对路径时 resolveSafe 必须兜底拦截
        Path evil = Path.of("..", "secret.md");
        assertThrows(InvalidRequestException.class, () -> VaultPaths.resolveSafe(tempDir, evil));
    }
}
