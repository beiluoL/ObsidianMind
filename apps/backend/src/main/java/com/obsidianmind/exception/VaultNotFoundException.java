package com.obsidianmind.exception;

/**
 * Vault 不存在或已被移除（对应 HTTP 404）：连接路径失效、目录被删除等场景。
 */
public class VaultNotFoundException extends BusinessException {
    public VaultNotFoundException(String message) {
        super("VAULT_NOT_FOUND", message);
    }
}
