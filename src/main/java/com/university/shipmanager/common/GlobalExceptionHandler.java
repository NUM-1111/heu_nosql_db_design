package com.university.shipmanager.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获文件过大异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Map<String, Object> handleFileSizeLimit(MaxUploadSizeExceededException e) {
        return Map.of(
                "timestamp", System.currentTimeMillis(),
                "status", 500,
                "error", "File Too Large",
                "message", "文件大小超过限制！请上传小于 100MB 的文件。" // 👈 给前端的提示
        );
    }

    /**
     * 捕获其他运行时异常 (比如空指针、参数错误)
     */
    @ExceptionHandler(RuntimeException.class)
    public Map<String, Object> handleRuntimeException(RuntimeException e) {
        return Map.of(
                "status", 500,
                "error", "Server Error",
                "message", e.getMessage() // 把报错信息直接返回给前端显示
        );
    }
}