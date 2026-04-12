package com.bin.bilibrain.model.vo.catalog;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BiliSearchVideoItemVO(
    String bvid,
    String title,
    String upName,
    String description,
    String coverUrl,
    String durationText,
    int playCount,
    int favorites,
    String tagText,
    String publishedAt,
    String watchUrl
) {
}
