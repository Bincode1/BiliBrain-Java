package com.bin.bilibrain.bilibili;

import java.util.Map;

public record BilibiliQrPollPayload(
    String status,
    String message,
    Map<String, String> cookies
) {
}
