package com.bin.bilibrain.common;

import com.bin.bilibrain.exception.ErrorCode;

public final class ResultUtils {
    private ResultUtils() {
    }

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, "ok", data, null);
    }

    public static <T> BaseResponse<T> success(T data, String requestId) {
        return new BaseResponse<>(0, "ok", data, requestId);
    }

    public static BaseResponse<Void> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode.getCode(), errorCode.getMessage(), null, null);
    }

    public static BaseResponse<Void> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), message, null, null);
    }

    public static BaseResponse<Void> error(ErrorCode errorCode, String message, String requestId) {
        return new BaseResponse<>(errorCode.getCode(), message, null, requestId);
    }
}
