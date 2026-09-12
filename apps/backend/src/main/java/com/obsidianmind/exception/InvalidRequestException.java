package com.obsidianmind.exception;

/**
 * 请求参数不合法（对应 HTTP 400）：如缺少必填字段、格式错误等客户端输入问题。
 */
public class InvalidRequestException extends BusinessException {
    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message);
    }
}
