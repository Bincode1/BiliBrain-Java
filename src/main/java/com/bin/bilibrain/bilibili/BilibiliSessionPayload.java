package com.bin.bilibrain.bilibili;

public record BilibiliSessionPayload(
    boolean loggedIn,
    String userName,
    Long uid
) {
}
