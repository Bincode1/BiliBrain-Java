package com.bin.bilibrain.auth;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuthQrStartResponse(
    String qrcodeKey,
    String url,
    String svg
) {
}
