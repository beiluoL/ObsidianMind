package com.obsidianmind.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理：统一错误信封 {code, message, timestamp}。
 * 业务异常按语义映射 HTTP 状态码；未知异常一律 500 且不泄漏堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException e) {
        log.warn("业务异常 [{}]: {}", e.getCode(), e.getMessage());
        return ResponseEntity.status(statusOf(e.getCode())).body(body(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .findFirst()
                .orElse("请求参数非法");
        return ResponseEntity.badRequest().body(body("INVALID_REQUEST", message));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(body("INVALID_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception e) {
        log.error("未预期异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", "服务内部错误"));
    }

    private HttpStatus statusOf(String code) {
        return switch (code) {
            case "VAULT_NOT_FOUND", "NOTE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "VAULT_ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
            case "MODEL_NOT_FOUND", "PROVIDER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "MODEL_DISABLED", "PROVIDER_DISABLED", "PROVIDER_NOT_CONFIGURED" -> HttpStatus.CONFLICT;
            case "OLLAMA_UNAVAILABLE", "MILVUS_UNAVAILABLE", "EMBEDDING_ERROR", "VECTOR_STORE_ERROR",
                 "LLM_UNAVAILABLE" ->
                    HttpStatus.SERVICE_UNAVAILABLE;
            case "LLM_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "CONFIGURATION_ERROR", "EMBEDDING_DIMENSION_MISMATCH" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private Map<String, Object> body(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
