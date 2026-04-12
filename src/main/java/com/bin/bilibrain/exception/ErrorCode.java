package com.bin.bilibrain.exception;

public enum ErrorCode {
    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_FOUND_ERROR(40400, "资源不存在"),
    FORBIDDEN_ERROR(40300, "无权限访问"),
    UNAUTHORIZED_ERROR(40100, "未登录或登录已失效"),
    OPERATION_ERROR(50001, "操作失败"),
    SYSTEM_ERROR(50000, "系统内部异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
