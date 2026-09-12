package com.obsidianmind.dto.system;

/**
 * 系统状态响应（前端底部 ● Ollama / ● Milvus / ● Vault 数据源）。
 */
public record SystemStatusResponse(
        String application,
        String status,
        ComponentStatus ollama,
        ComponentStatus milvus,
        ComponentStatus vault) {

    /**
     * 组件状态。
     */
    public record ComponentStatus(String status) {
    }
}
