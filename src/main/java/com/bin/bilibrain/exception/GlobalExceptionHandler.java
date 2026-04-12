package com.bin.bilibrain.exception;

import com.bin.bilibrain.common.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> handleBusinessException(BusinessException exception) {
        log.warn("Business exception: code={}, message={}", exception.getCode(), exception.getMessage());
        return ResponseEntity.status(exception.getStatusCode())
            .body(new BaseResponse<>(exception.getCode(), exception.getMessage(), null, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodArgumentNotValidException(
        MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse(ErrorCode.PARAMS_ERROR.getMessage());
        return ResponseEntity.badRequest()
            .body(new BaseResponse<>(ErrorCode.PARAMS_ERROR.getCode(), message, null, null));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<BaseResponse<Void>> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse(ErrorCode.PARAMS_ERROR.getMessage());
        return ResponseEntity.badRequest()
            .body(new BaseResponse<>(ErrorCode.PARAMS_ERROR.getCode(), message, null, null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseResponse<Void>> handleConstraintViolationException(
        ConstraintViolationException exception
    ) {
        String message = exception.getConstraintViolations().stream()
            .findFirst()
            .map(violation -> violation.getMessage())
            .orElse(ErrorCode.PARAMS_ERROR.getMessage());
        return ResponseEntity.badRequest()
            .body(new BaseResponse<>(ErrorCode.PARAMS_ERROR.getCode(), message, null, null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<BaseResponse<Void>> handleResponseStatusException(ResponseStatusException exception) {
        String message = exception.getReason() == null ? exception.getStatusCode().toString() : exception.getReason();
        log.warn("HTTP exception: status={}, message={}", exception.getStatusCode(), message);
        return ResponseEntity.status(exception.getStatusCode())
            .body(new BaseResponse<>(exception.getStatusCode().value(), message, null, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.internalServerError()
            .body(new BaseResponse<>(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage(), null, null));
    }
}
