package com.bin.bilibrain.model.vo.catalog;

import java.util.List;

public record FolderVideosResponse(
    FolderSummaryResponse folder,
    List<String> fields,
    List<VideoListItemResponse> videos,
    boolean cached,
    boolean stale
) {
}

