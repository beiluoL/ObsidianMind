package com.obsidianmind.exception;

public class VaultNotFoundException extends BusinessException {
    public VaultNotFoundException(String message) {
        super("VAULT_NOT_FOUND", message);
    }
}
