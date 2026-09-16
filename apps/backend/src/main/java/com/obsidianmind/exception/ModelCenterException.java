package com.obsidianmind.exception;

/**
 * Model Center 业务异常（Phase 5.5）：Provider / Model 配置、路由、凭据类错误。
 * 错误码：MODEL_NOT_FOUND / PROVIDER_NOT_FOUND / MODEL_DISABLED / PROVIDER_DISABLED /
 * PROVIDER_NOT_CONFIGURED / INVALID_REQUEST / NOT_SUPPORTED / PROVIDER_UNAVAILABLE。
 * 消息必须可直接展示给用户——不得包含 API Key、Authorization 或上游错误体原文。
 */
public class ModelCenterException extends BusinessException {

    public ModelCenterException(String code, String message) {
        super(code, message);
    }
}
