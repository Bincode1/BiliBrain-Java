package com.bin.bilibrain.bilibili;

import java.time.LocalDateTime;

public record BilibiliSearchVideo(
    String bvid,
    String title,
    String upName,
    String description,
    String coverUrl,
    String durationText,
    int playCount,
    int favorites,
    String tagText,
    LocalDateTime publishedAt,
    String watchUrl
) {
}
