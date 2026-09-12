package com.obsidianmind.exception;

/**
 * 业务异常基类：携带稳定错误码，由 GlobalExceptionHandler 统一转换为 HTTP 响应。
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
