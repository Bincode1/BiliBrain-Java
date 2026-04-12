package com.bin.bilibrain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class BusinessException extends RuntimeException {
    private final int code;
    private final HttpStatusCode statusCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.statusCode = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.statusCode = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(ErrorCode errorCode, String message, HttpStatusCode statusCode) {
        super(message);
        this.code = errorCode.getCode();
        this.statusCode = statusCode;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.statusCode = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(int code, String message, HttpStatusCode statusCode) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }

    public int getCode() {
        return code;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
