package com.bin.bilibrain.model.vo.chat;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatSourceVO(
    String sourceType,
    String bvid,
    Long folderId,
    String videoTitle,
    String upName,
    Double startSeconds,
    Double endSeconds,
    String excerpt
) {
}
