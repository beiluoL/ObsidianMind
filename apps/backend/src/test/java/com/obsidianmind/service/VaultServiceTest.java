package com.obsidianmind.service;

import com.obsidianmind.exception.InvalidRequestException;
import com.obsidianmind.exception.VaultNotFoundException;
import com.obsidianmind.repository.VaultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VaultService 测试：路径校验（存在 / 目录 / 可读）与扫描元数据。
 */
class VaultServiceTest {

    @TempDir
    Path vaultDir;

    private VaultService service() {
        return new VaultService(new VaultRepository(),
                new com.obsidianmind.config.ObsidianProperties(
                        new com.obsidianmind.config.ObsidianProperties.Vault("")));
    }

    @Test
    void shouldRejectNonexistentPath() {
        assertThrows(VaultNotFoundException.class,
                () -> service().connect("/definitely/not/exist/path"));
    }

    @Test
    void shouldRejectFileInsteadOfDirectory() throws Exception {
        Path file = vaultDir.resolve("a-file.md");
        Files.writeString(file, "text");
        assertThrows(InvalidRequestException.class, () -> service().connect(file.toString()));
    }

    @Test
    void shouldConnectAndReportInfo() {
        VaultService vaultService = service();
        vaultService.connect(vaultDir.toString());
        var info = vaultService.info();
        assertTrue(info.connected());
        assertEquals(vaultDir.getFileName().toString(), info.name());
    }

    @Test
    void shouldScanMetadataOnlyAndCountFolders() throws Exception {
        Path ai = Files.createDirectories(vaultDir.resolve("AI"));
        Path hidden = Files.createDirectories(vaultDir.resolve(".obsidian"));
        Files.writeString(ai.resolve("RAG.md"), "# RAG");
        Files.writeString(hidden.resolve("config.md"), "# 忽略隐藏目录");
        VaultService vaultService = service();
        vaultService.connect(vaultDir.toString());
        var scan = vaultService.scan();
        assertEquals(1, scan.markdownFiles());
        assertEquals(1, scan.folders());
        assertTrue(scan.durationMs() >= 0);
        assertFalse(vaultService.info().path().isEmpty());
    }

    @Test
    void shouldFailScanWhenNotConnected() {
        assertThrows(Exception.class, () -> service().scan());
    }
}
