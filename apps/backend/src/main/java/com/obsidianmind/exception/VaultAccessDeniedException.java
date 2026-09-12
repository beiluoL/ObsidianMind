package com.obsidianmind.exception;

public class VaultAccessDeniedException extends BusinessException {
    public VaultAccessDeniedException(String message) {
        super("VAULT_ACCESS_DENIED", message);
    }
}
