package com.bin.bilibrain.catalog;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FolderSyncResponse(
    Long uid,
    int newFolders,
    int updatedFolders,
    List<String> logs,
    CatalogStatsResponse stats
) {
}
