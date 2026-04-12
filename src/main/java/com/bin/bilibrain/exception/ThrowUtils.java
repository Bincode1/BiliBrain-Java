package com.bin.bilibrain.exception;

import org.springframework.http.HttpStatusCode;

public final class ThrowUtils {
    private ThrowUtils() {
    }

    public static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition) {
            throw new BusinessException(errorCode);
        }
    }

    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        if (condition) {
            throw new BusinessException(errorCode, message);
        }
    }

    public static void throwIf(boolean condition, ErrorCode errorCode, String message, HttpStatusCode statusCode) {
        if (condition) {
            throw new BusinessException(errorCode, message, statusCode);
        }
    }
}
