package com.bin.bilibrain.bilibili;

public record BilibiliAudioTrack(
    Long cid,
    String audioUrl,
    String mimeType,
    int bandwidth,
    String trackId
) {
}
