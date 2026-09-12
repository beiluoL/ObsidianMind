package com.obsidianmind.domain;

import java.time.Instant;

/**
 * 已连接 Vault 元信息。
 */
public record Vault(
        String name,
        String path,
        boolean connected,
        int noteCount,
        int folderCount,
        Instant lastScanAt
) {
}
