package com.bin.bilibrain.model.vo.ingestion;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProcessStatusResponse(
    String bvid,
    String title,
    int duration,
    double durationMinutes,
    int maxVideoMinutes,
    boolean overLimit,
    String overallStatus,
    String overallStatusLabel,
    String actionLabel,
    String errorMsg,
    int chunkCount,
    boolean running,
    String operation,
    boolean resetRunning,
    String resetStatus,
    String queueStatus,
    Long queueTaskId,
    List<ProcessStepItemResponse> steps,
    String transcriptSource,
    int transcriptSegmentCount,
    String transcriptUpdatedAt,
    boolean hasTranscript,
    boolean hasSummary,
    String summaryUpdatedAt,
    List<String> manualTags,
    String audioStorageProvider,
    String audioObjectKey,
    String audioUrl,
    boolean started,
    boolean reset
) {
    public ProcessStatusResponse withStarted(boolean started) {
        return new ProcessStatusResponse(
            bvid,
            title,
            duration,
            durationMinutes,
            maxVideoMinutes,
            overLimit,
            overallStatus,
            overallStatusLabel,
            actionLabel,
            errorMsg,
            chunkCount,
            running,
            operation,
            resetRunning,
            resetStatus,
            queueStatus,
            queueTaskId,
            steps,
            transcriptSource,
            transcriptSegmentCount,
            transcriptUpdatedAt,
            hasTranscript,
            hasSummary,
            summaryUpdatedAt,
            manualTags,
            audioStorageProvider,
            audioObjectKey,
            audioUrl,
            started,
            reset
        );
    }

    public ProcessStatusResponse withReset(boolean reset) {
        return new ProcessStatusResponse(
            bvid,
            title,
            duration,
            durationMinutes,
            maxVideoMinutes,
            overLimit,
            overallStatus,
            overallStatusLabel,
            actionLabel,
            errorMsg,
            chunkCount,
            running,
            operation,
            resetRunning,
            resetStatus,
            queueStatus,
            queueTaskId,
            steps,
            transcriptSource,
            transcriptSegmentCount,
            transcriptUpdatedAt,
            hasTranscript,
            hasSummary,
            summaryUpdatedAt,
            manualTags,
            audioStorageProvider,
            audioObjectKey,
            audioUrl,
            started,
            reset
        );
    }
}

