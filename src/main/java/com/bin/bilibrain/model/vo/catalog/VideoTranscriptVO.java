package com.bin.bilibrain.model.vo.catalog;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VideoTranscriptVO(
    String bvid,
    String title,
    String transcriptSource,
    int segmentCount,
    String text,
    String updatedAt,
    boolean cached
) {
}
