package com.bin.bilibrain.model.vo.summary;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VideoSummaryVO(
    String bvid,
    boolean available,
    boolean upToDate,
    boolean generated,
    String transcriptHash,
    String summaryText,
    String updatedAt
) {
}
