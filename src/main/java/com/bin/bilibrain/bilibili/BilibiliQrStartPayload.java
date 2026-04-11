package com.bin.bilibrain.bilibili;

public record BilibiliQrStartPayload(
    String qrcodeKey,
    String url,
    String svg
) {
}
