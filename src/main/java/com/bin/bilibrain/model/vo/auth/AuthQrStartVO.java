package com.bin.bilibrain.model.vo.auth;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuthQrStartVO(
    String qrcodeKey,
    String url,
    String svg
) {
}
