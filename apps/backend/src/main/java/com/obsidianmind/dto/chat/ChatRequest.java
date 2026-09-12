package com.obsidianmind.dto.chat;

import jakarta.validation.constraints.NotBlank;

/**
 * 聊天请求。
 */
public record ChatRequest(@NotBlank(message = "message 不能为空") String message, String scope) {

    public String scopeOrDefault() {
        return scope == null || scope.isBlank() ? "VAULT" : scope;
    }
}
