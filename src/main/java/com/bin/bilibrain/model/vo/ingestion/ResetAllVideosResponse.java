package com.bin.bilibrain.model.vo.ingestion;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ResetAllVideosResponse(
    boolean reset,
    int videoCount,
    int transcriptCount,
    int summaryCount,
    int pipelineCount,
    int taskCount
) {
}
