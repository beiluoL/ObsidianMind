package com.obsidianmind.exception;

/**
 * 配置非法（对应 HTTP 500）：如 chunk overlap ≥ size、向量维度非正数等启动期/运行期可发现的配置矛盾。
 */
public class ConfigurationException extends BusinessException {
    public ConfigurationException(String message) {
        super("CONFIGURATION_ERROR", message);
    }
}
