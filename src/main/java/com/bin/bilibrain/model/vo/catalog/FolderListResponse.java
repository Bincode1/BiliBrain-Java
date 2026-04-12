package com.bin.bilibrain.model.vo.catalog;

import java.util.List;

public record FolderListResponse(
    List<FolderSummaryResponse> folders,
    CatalogStatsResponse stats,
    boolean cached,
    boolean stale
) {
}

