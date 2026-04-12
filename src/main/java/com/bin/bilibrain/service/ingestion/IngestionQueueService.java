package com.bin.bilibrain.service.ingestion;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.model.entity.IngestionTask;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.mapper.IngestionTaskMapper;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
import com.bin.bilibrain.mapper.VideoSummaryMapper;
import com.bin.bilibrain.model.vo.ingestion.ResetAllVideosResponse;
import com.bin.bilibrain.service.retrieval.VectorSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class IngestionQueueService {
    private final IngestionTaskMapper ingestionTaskMapper;
    private final VideoMapper videoMapper;
    private final TranscriptMapper transcriptMapper;
    private final VideoSummaryMapper videoSummaryMapper;
    private final VideoPipelineMapper videoPipelineMapper;
    private final IngestionDispatcherService ingestionDispatcherService;
    private final VectorSearchService vectorSearchService;
    private final JdbcTemplate jdbcTemplate;
    @Qualifier("applicationTaskExecutor")
    private final Executor executor;

    private final Map<String, CompletableFuture<Void>> resetTasks = new ConcurrentHashMap<>();
    private final Map<String, ResetTaskState> resetStates = new ConcurrentHashMap<>();

    public boolean enqueueProcessing(String bvid) {
        Video video = requireVideo(bvid);
        if (video.getIsInvalid() != null && video.getIsInvalid() != 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "失效视频无法开始处理。", HttpStatus.BAD_REQUEST);
        }

        IngestionTask activeTask = ingestionTaskMapper.findLatestActiveByBvid(bvid);
        if (activeTask != null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        ingestionTaskMapper.insert(IngestionTask.builder()
            .bvid(bvid)
            .operation("process")
            .status("queued")
            .errorMsg("")
            .createdAt(now)
            .updatedAt(now)
            .build());
        ingestionDispatcherService.kick();
        return true;
    }

    public boolean resetVideoProcessing(String bvid) {
        requireVideo(bvid);
        CompletableFuture<Void> activeReset = resetTasks.get(bvid);
        if (activeReset != null && !activeReset.isDone()) {
            return false;
        }

        cancelActiveProcess(bvid);
        resetStates.put(bvid, new ResetTaskState("queued", ""));
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> executeReset(bvid), executor)
            .whenComplete((ignored, throwable) -> resetTasks.remove(bvid));
        resetTasks.put(bvid, future);
        return true;
    }

    @Transactional
    public ResetAllVideosResponse resetAllVideoProcessing() {
        cancelAllActiveTasks();
        videoMapper.selectList(null).forEach(video -> vectorSearchService.deleteByBvid(video.getBvid()));
        int transcriptCount = jdbcTemplate.update("DELETE FROM transcripts");
        int summaryCount = jdbcTemplate.update("DELETE FROM video_summaries");
        int pipelineCount = jdbcTemplate.update("DELETE FROM video_pipeline");
        int taskCount = jdbcTemplate.update("DELETE FROM ingestion_tasks");
        int videoCount = videoMapper.update(
            null,
            new LambdaUpdateWrapper<Video>()
                .set(Video::getSyncedAt, null)
                .set(Video::getSubtitleSource, null)
                .set(Video::getAudioStorageProvider, null)
                .set(Video::getAudioObjectKey, null)
                .set(Video::getAudioUploadedAt, null)
        );
        resetStates.clear();
        resetTasks.clear();
        return new ResetAllVideosResponse(
            true,
            videoCount,
            transcriptCount,
            summaryCount,
            pipelineCount,
            taskCount
        );
    }

    public ResetTaskState getResetState(String bvid) {
        return resetStates.get(bvid);
    }

    private void executeReset(String bvid) {
        try {
            resetStates.put(bvid, new ResetTaskState("running", ""));
            vectorSearchService.deleteByBvid(bvid);
            transcriptMapper.deleteByBvid(bvid);
            videoSummaryMapper.deleteByBvid(bvid);
            videoPipelineMapper.deleteByBvid(bvid);
            ingestionTaskMapper.deleteByBvid(bvid);

            Video video = requireVideo(bvid);
            video.setSyncedAt(null);
            video.setSubtitleSource(null);
            video.setAudioStorageProvider(null);
            video.setAudioObjectKey(null);
            video.setAudioUploadedAt(null);
            video.setUpdatedAt(LocalDateTime.now());
            videoMapper.updateById(video);

            resetStates.remove(bvid);
        } catch (Exception exception) {
            resetStates.put(bvid, new ResetTaskState("failed", exception.getMessage()));
        }
    }

    private void cancelActiveProcess(String bvid) {
        ingestionDispatcherService.cancelProcessing(bvid);
        ingestionTaskMapper.update(
            null,
            new LambdaUpdateWrapper<IngestionTask>()
                .eq(IngestionTask::getBvid, bvid)
                .in(IngestionTask::getStatus, "queued", "running")
                .set(IngestionTask::getStatus, "canceled")
                .set(IngestionTask::getUpdatedAt, LocalDateTime.now())
                .set(IngestionTask::getFinishedAt, LocalDateTime.now())
        );
    }

    private void cancelAllActiveTasks() {
        ingestionDispatcherService.cancelAllProcessing();
        ingestionTaskMapper.update(
            null,
            new LambdaUpdateWrapper<IngestionTask>()
                .in(IngestionTask::getStatus, "queued", "running")
                .set(IngestionTask::getStatus, "canceled")
                .set(IngestionTask::getUpdatedAt, LocalDateTime.now())
                .set(IngestionTask::getFinishedAt, LocalDateTime.now())
        );
    }

    private Video requireVideo(String bvid) {
        Video video = videoMapper.selectById(bvid);
        if (video == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "找不到这个视频，请先同步收藏夹元数据。", HttpStatus.NOT_FOUND);
        }
        return video;
    }
}


