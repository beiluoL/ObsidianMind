package com.obsidianmind.exception;

/**
 * Vault 访问被拒绝（对应 HTTP 403）：路径穿越/越权访问被 VaultPaths 校验拦截时抛出。
 */
public class VaultAccessDeniedException extends BusinessException {
    public VaultAccessDeniedException(String message) {
        super("VAULT_ACCESS_DENIED", message);
    }
}
