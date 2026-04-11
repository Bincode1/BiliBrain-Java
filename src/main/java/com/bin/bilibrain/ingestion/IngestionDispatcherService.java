package com.bin.bilibrain.ingestion;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.entity.IngestionTask;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.entity.VideoPipeline;
import com.bin.bilibrain.mapper.IngestionTaskMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
import com.bin.bilibrain.system.ProcessingSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionDispatcherService {
    private final IngestionTaskMapper ingestionTaskMapper;
    private final VideoPipelineMapper videoPipelineMapper;
    private final VideoMapper videoMapper;
    private final PipelineStateSupport pipelineStateSupport;
    private final ProcessingSettingsService processingSettingsService;
    private final AppProperties appProperties;
    @Qualifier("applicationTaskExecutor")
    private final Executor executor;

    private final Map<String, CompletableFuture<Void>> runtimeTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean dispatching = new AtomicBoolean(false);

    public void kick() {
        if (dispatching.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::dispatchLoop, executor);
        }
    }

    public void dispatchNow() {
        dispatchLoop();
    }

    public boolean hasRunningTask(String bvid) {
        CompletableFuture<Void> future = runtimeTasks.get(bvid);
        return future != null && !future.isDone();
    }

    public void cancelProcessing(String bvid) {
        CompletableFuture<Void> future = runtimeTasks.remove(bvid);
        if (future != null) {
            future.cancel(true);
        }
    }

    public void cancelAllProcessing() {
        List<String> bvids = runtimeTasks.keySet().stream().toList();
        for (String bvid : bvids) {
            cancelProcessing(bvid);
        }
    }

    private void dispatchLoop() {
        try {
            boolean launched;
            do {
                launched = false;
                while (runtimeTasks.size() < Math.max(appProperties.getProcessing().getIngestionMaxConcurrency(), 1)) {
                    IngestionTask task = claimNextQueuedTask();
                    if (task == null) {
                        break;
                    }
                    launchTask(task);
                    launched = true;
                }
            } while (launched);
        } finally {
            dispatching.set(false);
            if (!ingestionTaskMapper.findQueuedTasks(1).isEmpty() && runtimeTasks.size() < Math.max(appProperties.getProcessing().getIngestionMaxConcurrency(), 1)) {
                kick();
            }
        }
    }

    private IngestionTask claimNextQueuedTask() {
        List<IngestionTask> queuedTasks = ingestionTaskMapper.findQueuedTasks(1);
        if (queuedTasks.isEmpty()) {
            return null;
        }
        IngestionTask candidate = queuedTasks.get(0);
        LocalDateTime now = LocalDateTime.now();
        int updated = ingestionTaskMapper.update(
            null,
            new LambdaUpdateWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskId, candidate.getTaskId())
                .eq(IngestionTask::getStatus, "queued")
                .set(IngestionTask::getStatus, "running")
                .set(IngestionTask::getStartedAt, now)
                .set(IngestionTask::getHeartbeatAt, now)
                .set(IngestionTask::getUpdatedAt, now)
        );
        if (updated != 1) {
            return null;
        }
        candidate.setStatus("running");
        candidate.setStartedAt(now);
        candidate.setHeartbeatAt(now);
        candidate.setUpdatedAt(now);
        return candidate;
    }

    private void launchTask(IngestionTask task) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> runShellTask(task), executor)
            .whenComplete((ignored, throwable) -> {
                runtimeTasks.remove(task.getBvid());
                kick();
            });
        runtimeTasks.put(task.getBvid(), future);
    }

    private void runShellTask(IngestionTask task) {
        String bvid = task.getBvid();
        Video video = videoMapper.selectById(bvid);
        if (video == null) {
            markTaskFinished(task.getTaskId(), "failed", "视频不存在，无法开始处理。");
            return;
        }

        Map<String, Map<String, Object>> state = loadPipelineState(bvid);
        try {
            int maxVideoMinutes = processingSettingsService.getSettings().maxVideoMinutes();
            if ((video.getDuration() != null ? video.getDuration() : 0) > maxVideoMinutes * 60) {
                pipelineStateSupport.markAudioFailed(state, "视频时长超过当前全局限制，请先调整配置后重试。");
                savePipelineState(bvid, state);
                markTaskFinished(task.getTaskId(), "failed", pipelineStateSupport.pipelineErrorMessage(state));
                return;
            }

            pipelineStateSupport.markAudioRunning(state);
            savePipelineState(bvid, state);

            pipelineStateSupport.markAudioShellCompleted(state);
            savePipelineState(bvid, state);

            video.setUpdatedAt(LocalDateTime.now());
            videoMapper.updateById(video);
            markTaskFinished(task.getTaskId(), "succeeded", "");
        } catch (Exception exception) {
            log.warn("ingestion shell failed for {}", bvid, exception);
            pipelineStateSupport.markAudioFailed(state, exception.getMessage());
            savePipelineState(bvid, state);
            markTaskFinished(task.getTaskId(), "failed", exception.getMessage());
        }
    }

    private Map<String, Map<String, Object>> loadPipelineState(String bvid) {
        VideoPipeline pipeline = videoPipelineMapper.selectById(bvid);
        Map<String, Map<String, Object>> state = pipelineStateSupport.readState(pipeline == null ? null : pipeline.getStateJson());
        Video video = videoMapper.selectById(bvid);
        pipelineStateSupport.hydrateAudioStep(video, state);
        return state;
    }

    private void savePipelineState(String bvid, Map<String, Map<String, Object>> state) {
        LocalDateTime now = LocalDateTime.now();
        String overallStatus = pipelineStateSupport.overallStatus(state);
        String stateJson = pipelineStateSupport.writeState(state);
        VideoPipeline existing = videoPipelineMapper.selectById(bvid);
        if (existing == null) {
            videoPipelineMapper.insert(VideoPipeline.builder()
                .bvid(bvid)
                .overallStatus(overallStatus)
                .stateJson(stateJson)
                .updatedAt(now)
                .build());
            return;
        }
        existing.setOverallStatus(overallStatus);
        existing.setStateJson(stateJson);
        existing.setUpdatedAt(now);
        videoPipelineMapper.updateById(existing);
    }

    private void markTaskFinished(Long taskId, String status, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        ingestionTaskMapper.update(
            null,
            new LambdaUpdateWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskId, taskId)
                .set(IngestionTask::getStatus, status)
                .set(IngestionTask::getErrorMsg, errorMessage)
                .set(IngestionTask::getHeartbeatAt, now)
                .set(IngestionTask::getFinishedAt, now)
                .set(IngestionTask::getUpdatedAt, now)
        );
    }
}
