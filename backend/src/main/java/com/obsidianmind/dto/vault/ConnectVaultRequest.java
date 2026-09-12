package com.obsidianmind.dto.vault;

import jakarta.validation.constraints.NotBlank;

/**
 * 连接 Vault 请求。
 */
public record ConnectVaultRequest(@NotBlank(message = "path 不能为空") String path) {
}
