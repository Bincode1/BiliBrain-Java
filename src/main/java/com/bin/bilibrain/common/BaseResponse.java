package com.bin.bilibrain.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaseResponse<T>(
    int code,
    String message,
    T data,
    String requestId
) {
}
