package com.bin.bilibrain.catalog;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VideoListItemResponse(
    String bvid,
    Long folderId,
    String title,
    String upName,
    String coverUrl,
    int duration,
    String publishedAt,
    Long cid,
    List<String> manualTags,
    String transcriptSource,
    int transcriptSegmentCount,
    String transcriptUpdatedAt,
    String syncStatus,
    int chunkCount,
    boolean hasSummary,
    String syncedAt,
    String errorMsg,
    boolean isInvalid,
    String createdAt,
    VideoPipelineResponse pipeline
) {
}
