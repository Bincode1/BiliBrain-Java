package com.bin.bilibrain.model.vo.ingestion;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProcessStepItemResponse(
    String step,
    String label,
    String status,
    String statusLabel,
    String updatedAt,
    String error,
    String substageLabel,
    int count,
    int segmentCount
) {
}

