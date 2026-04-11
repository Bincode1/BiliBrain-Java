package com.bin.bilibrain.ingestion;

import com.bin.bilibrain.entity.IngestionTask;
import com.bin.bilibrain.entity.Transcript;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.entity.VideoPipeline;
import com.bin.bilibrain.entity.VideoSummary;
import com.bin.bilibrain.mapper.IngestionTaskMapper;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
import com.bin.bilibrain.mapper.VideoSummaryMapper;
import com.bin.bilibrain.service.media.AudioStorageService;
import com.bin.bilibrain.system.ProcessingSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PipelineStatusService {
    private final VideoMapper videoMapper;
    private final TranscriptMapper transcriptMapper;
    private final VideoSummaryMapper videoSummaryMapper;
    private final VideoPipelineMapper videoPipelineMapper;
    private final IngestionTaskMapper ingestionTaskMapper;
    private final ProcessingSettingsService processingSettingsService;
    private final PipelineStateSupport pipelineStateSupport;
    private final IngestionDispatcherService ingestionDispatcherService;
    private final IngestionQueueService ingestionQueueService;
    private final AudioStorageService audioStorageService;

    public ProcessStatusResponse getStatus(String bvid) {
        Video video = videoMapper.selectById(bvid);
        if (video == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到这个视频，请先同步收藏夹元数据。");
        }
        return buildStatus(video);
    }

    public VideoProcessSnapshot getCatalogSnapshot(Video video) {
        ProcessStatusResponse status = buildStatus(video);
        VideoPipeline pipeline = videoPipelineMapper.selectById(video.getBvid());
        return new VideoProcessSnapshot(
            status.overallStatus(),
            status.transcriptSource(),
            status.transcriptSegmentCount(),
            status.transcriptUpdatedAt(),
            status.chunkCount(),
            status.hasSummary(),
            status.summaryUpdatedAt(),
            status.errorMsg(),
            status.manualTags(),
            pipelineStateSupport.toPipelineResponse(loadState(video, pipeline, transcriptMapper.findByBvid(video.getBvid())))
        );
    }

    private ProcessStatusResponse buildStatus(Video video) {
        Transcript transcript = transcriptMapper.findByBvid(video.getBvid());
        VideoSummary summary = videoSummaryMapper.selectById(video.getBvid());
        VideoPipeline pipeline = videoPipelineMapper.selectById(video.getBvid());
        IngestionTask activeTask = ingestionTaskMapper.findLatestActiveByBvid(video.getBvid());
        Map<String, Map<String, Object>> state = loadState(video, pipeline, transcript);
        String pipelineOverallStatus = pipelineStateSupport.overallStatus(state);

        ResetTaskState resetState = ingestionQueueService.getResetState(video.getBvid());
        boolean resetRunning = resetState != null && List.of("queued", "running").contains(resetState.status());
        boolean processRunning = activeTask != null || ingestionDispatcherService.hasRunningTask(video.getBvid());
        boolean legacyIndexed = pipeline == null && video.getSyncedAt() != null;
        String effectiveOverallStatus = processRunning
            ? "processing"
            : (resetRunning ? "pending" : (legacyIndexed ? "indexed" : pipelineOverallStatus));
        String errorMsg = resetState != null && !safe(resetState.error()).isBlank()
            ? safe(resetState.error())
            : pipelineStateSupport.pipelineErrorMessage(state);

        int maxVideoMinutes = processingSettingsService.getSettings().maxVideoMinutes();
        int duration = video.getDuration() == null ? 0 : video.getDuration();
        boolean overLimit = duration > maxVideoMinutes * 60;
        String operation = resetState != null ? "reset" : (processRunning ? "process" : null);

        return new ProcessStatusResponse(
            video.getBvid(),
            safe(video.getTitle()).isBlank() ? video.getBvid() : video.getTitle(),
            duration,
            duration == 0 ? 0 : Math.round((duration / 60.0) * 10.0) / 10.0,
            maxVideoMinutes,
            overLimit,
            effectiveOverallStatus,
            pipelineStateSupport.overallStatusLabel(effectiveOverallStatus),
            pipelineStateSupport.actionLabel(effectiveOverallStatus, activeTask == null ? null : activeTask.getStatus()),
            errorMsg,
            pipelineStateSupport.chunkCount(state),
            resetRunning || processRunning,
            operation,
            resetRunning,
            resetState == null ? null : resetState.status(),
            activeTask == null ? null : activeTask.getStatus(),
            activeTask == null ? null : activeTask.getTaskId(),
            pipelineStateSupport.stepItems(state),
            transcript != null && !safe(transcript.getSourceModel()).isBlank()
                ? transcript.getSourceModel()
                : defaultTranscriptSource(state),
            transcript != null ? intValue(transcript.getSegmentCount()) : pipelineStateSupport.transcriptSegmentCount(state),
            transcript != null ? formatDateTime(transcript.getUpdatedAt()) : pipelineStateSupport.transcriptUpdatedAt(state),
            transcript != null && (!safe(transcript.getTranscriptText()).isBlank() || transcript.getUpdatedAt() != null),
            summary != null && !safe(summary.getSummaryText()).isBlank(),
            summary == null ? "" : formatDateTime(summary.getUpdatedAt()),
            parseManualTags(video.getManualTags()),
            safe(video.getAudioStorageProvider()),
            safe(video.getAudioObjectKey()),
            audioStorageService.getAudioUrl(video.getAudioObjectKey()),
            false,
            false
        );
    }

    private Map<String, Map<String, Object>> loadState(Video video, VideoPipeline pipeline, Transcript transcript) {
        Map<String, Map<String, Object>> state = pipelineStateSupport.readState(pipeline == null ? null : pipeline.getStateJson());
        pipelineStateSupport.hydrateAudioStep(video, state);
        pipelineStateSupport.hydrateTranscriptStep(transcript, state);
        return state;
    }

    private List<String> parseManualTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,，\\n]+"))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .distinct()
            .toList();
    }

    private String defaultTranscriptSource(Map<String, Map<String, Object>> state) {
        String source = pipelineStateSupport.transcriptSource(state);
        return source.isBlank() ? "未转写" : source;
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
