package com.dianping.exception;

import com.dianping.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(Result.error(e.getStatus(), e.getMessage()));
    }

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException e) {
        log.debug("客户端已断开连接: {}", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException e) {

        FieldError fieldError =
                e.getBindingResult().getFieldError();

        String message =
                fieldError != null
                        ? fieldError.getDefaultMessage()
                        : "参数校验失败";



        return ResponseEntity.badRequest().body(Result.error(400, message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleInvalidRequest(Exception e) {
        return ResponseEntity.badRequest().body(Result.error(400, "请求参数不合法"));
    }

    @ExceptionHandler({org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<Result<Void>> handleProtocolError(Exception e) {
        org.springframework.web.ErrorResponse error = (org.springframework.web.ErrorResponse)e;
        int status = error.getStatusCode().value();
        return ResponseEntity.status(status).headers(error.getHeaders())
                .body(Result.error(status, "请求方法或媒体类型不支持"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(
            Exception e) {

        log.error(
                "系统异常",
                e
        );

        return ResponseEntity.internalServerError().body(Result.error(500, "系统异常，请稍后重试"));
    }
}
