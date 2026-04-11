package com.bin.bilibrain.bilibili;

import java.time.LocalDateTime;

public record BilibiliVideoMetadata(
    String bvid,
    String title,
    String upName,
    String coverUrl,
    int duration,
    boolean invalid,
    LocalDateTime publishedAt
) {
}
