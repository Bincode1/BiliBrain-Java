package com.bin.bilibrain.catalog;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VideoPipelineStepResponse(
    String status,
    String updatedAt,
    String error,
    String substageLabel,
    int count,
    int segmentCount
) {
}
