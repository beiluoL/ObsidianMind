package com.obsidianmind.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 聊天请求（Phase 5 RAG + Phase 5.5 模型路由）。
 * message 即用户问题；topK 可选（null = 服务端默认，超上限由 RetrievalService 夹取，不报错）；
 * modelId 可选（null = Model Center 默认模型；无默认 → legacy 配置文件 Ollama）。
 * 安全边界：客户端只能指定 modelId——apiKey / baseUrl 永不接受，凭据由后端 CredentialResolver 解析。
 * scope 为 Phase 1 遗留字段，当前 RAG 链路未使用（保留以兼容旧前端，仅记录日志）。
 */
public record ChatRequest(
        @NotBlank(message = "message 不能为空")
        @Size(max = 512, message = "message 过长（上限 512 字符）")
        String message,
        String scope,
        @Positive(message = "topK 必须为正整数")
        Integer topK,
        @Size(max = 64, message = "modelId 过长")
        String modelId) {

    public String scopeOrDefault() {
        return scope == null || scope.isBlank() ? "VAULT" : scope;
    }
}
