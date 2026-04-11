package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.entity.Transcript;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.ingestion.PipelineStateSupport;
import com.bin.bilibrain.service.asr.AudioTranscriptionService;
import com.bin.bilibrain.service.media.AudioStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AsrTranscribeNode implements NodeAction {
    private final IngestionGraphStateStore stateStore;
    private final PipelineStateSupport pipelineStateSupport;
    private final AudioStorageService audioStorageService;
    private final AudioTranscriptionService audioTranscriptionService;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String bvid = IngestionState.requireBvid(state);
        Video video = IngestionState.resolveVideo(state);
        if (video == null) {
            video = stateStore.requireVideo(bvid);
        }

        Transcript existingTranscript = stateStore.findTranscript(bvid);
        Map<String, Map<String, Object>> pipelineState = stateStore.loadPipelineState(bvid, video, existingTranscript);

        if (existingTranscript != null && StringUtils.hasText(existingTranscript.getTranscriptText())) {
            pipelineStateSupport.markTranscriptDone(
                pipelineState,
                existingTranscript.getSourceModel(),
                existingTranscript.getSegmentCount() == null ? 0 : existingTranscript.getSegmentCount()
            );
            stateStore.savePipelineState(bvid, pipelineState);
            return buildUpdates(video, true);
        }

        String audioPath = resolveAudioPath(state, video);
        if (!StringUtils.hasText(audioPath)) {
            pipelineStateSupport.markTranscriptFailed(pipelineState, "当前视频缺少可用音频缓存，请先重试音频下载。");
            stateStore.savePipelineState(bvid, pipelineState);
            throw new IllegalStateException("当前视频缺少可用音频缓存，请先重试音频下载。");
        }

        try {
            AudioTranscriptionService.AudioTranscriptionResult result = audioTranscriptionService.transcribe(
                Path.of(audioPath),
                progress -> {
                    pipelineStateSupport.markTranscriptRunning(
                        pipelineState,
                        audioTranscriptionService.resolveSourceModel(),
                        progress.message()
                    );
                    stateStore.savePipelineState(bvid, pipelineState);
                }
            );

            LocalDateTime now = LocalDateTime.now();
            Transcript transcript = Transcript.builder()
                .bvid(bvid)
                .sourceModel(result.model())
                .segmentCount(result.segmentCount())
                .transcriptText(result.text())
                .segmentsJson(writeSegmentsJson(result.segments()))
                .createdAt(existingTranscript == null || existingTranscript.getCreatedAt() == null ? now : existingTranscript.getCreatedAt())
                .updatedAt(now)
                .build();
            stateStore.saveTranscript(transcript);

            pipelineStateSupport.markTranscriptDone(pipelineState, result.model(), result.segmentCount());
            stateStore.savePipelineState(bvid, pipelineState);
            return buildUpdates(video, true);
        } catch (Exception exception) {
            pipelineStateSupport.markTranscriptFailed(pipelineState, exception.getMessage());
            stateStore.savePipelineState(bvid, pipelineState);
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private String resolveAudioPath(OverAllState state, Video video) {
        String audioPath = IngestionState.resolveAudioPath(state);
        if (StringUtils.hasText(audioPath)) {
            return audioPath;
        }
        if (audioStorageService.exists(video.getAudioStorageProvider(), video.getAudioObjectKey())) {
            return audioStorageService.resolvePath(video.getAudioStorageProvider(), video.getAudioObjectKey()).toString();
        }
        return "";
    }

    private String writeSegmentsJson(Object segments) {
        try {
            return objectMapper.writeValueAsString(segments);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化转写分段结果失败。", exception);
        }
    }

    private Map<String, Object> buildUpdates(Video video, boolean transcriptPresent) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(IngestionState.VIDEO, video);
        updates.put(IngestionState.TRANSCRIPT_PRESENT, transcriptPresent);
        return updates;
    }
}
