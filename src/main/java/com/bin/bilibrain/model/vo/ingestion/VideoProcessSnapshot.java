package com.bin.bilibrain.model.vo.ingestion;

import com.bin.bilibrain.model.vo.catalog.VideoPipelineResponse;

import java.util.List;

public record VideoProcessSnapshot(
    String syncStatus,
    String transcriptSource,
    int transcriptSegmentCount,
    String transcriptUpdatedAt,
    int chunkCount,
    boolean hasSummary,
    String summaryUpdatedAt,
    String errorMsg,
    List<String> manualTags,
    VideoPipelineResponse pipeline
) {
}


