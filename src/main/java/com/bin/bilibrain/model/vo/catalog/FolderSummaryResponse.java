package com.bin.bilibrain.model.vo.catalog;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FolderSummaryResponse(
    Long folderId,
    String title,
    Integer mediaCount,
    String updatedAt
) {
}

