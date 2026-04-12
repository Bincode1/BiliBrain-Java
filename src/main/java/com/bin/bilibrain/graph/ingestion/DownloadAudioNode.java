package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.service.ingestion.PipelineStateSupport;
import com.bin.bilibrain.service.media.AudioDownloadService;
import com.bin.bilibrain.service.media.AudioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DownloadAudioNode implements NodeAction {
    private final IngestionGraphStateStore stateStore;
    private final PipelineStateSupport pipelineStateSupport;
    private final AudioDownloadService audioDownloadService;
    private final AudioStorageService audioStorageService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String bvid = IngestionState.requireBvid(state);
        Video video = IngestionState.resolveVideo(state);
        if (video == null) {
            video = stateStore.requireVideo(bvid);
        }

        Transcript transcript = stateStore.findTranscript(bvid);
        Map<String, Map<String, Object>> pipelineState = stateStore.loadPipelineState(bvid, video, transcript);

        try {
            String cachedProvider = video.getAudioStorageProvider();
            String cachedObjectKey = video.getAudioObjectKey();
            if (audioStorageService.exists(cachedProvider, cachedObjectKey)) {
                pipelineStateSupport.markAudioDone(pipelineState, cachedObjectKey, "复用本地音频缓存");
                stateStore.savePipelineState(bvid, pipelineState);
                return buildUpdates(video, cachedProvider, cachedObjectKey, transcript != null);
            }

            pipelineStateSupport.markAudioRunning(pipelineState, "正在从 B 站下载音频");
            stateStore.savePipelineState(bvid, pipelineState);

            AudioDownloadService.DownloadedAudio downloadedAudio = audioDownloadService.download(bvid);
            try {
                pipelineStateSupport.markAudioRunning(pipelineState, "正在写入本地音频缓存");
                stateStore.savePipelineState(bvid, pipelineState);

                AudioStorageService.AudioObjectRef audioObjectRef = audioStorageService.uploadAudio(downloadedAudio.filePath(), bvid);
                video.setCid(downloadedAudio.cid() == null ? video.getCid() : downloadedAudio.cid());
                video.setAudioStorageProvider(audioObjectRef.provider());
                video.setAudioObjectKey(audioObjectRef.objectKey());
                video.setAudioUploadedAt(LocalDateTime.now());
                video.setUpdatedAt(LocalDateTime.now());
                Video savedVideo = stateStore.saveVideo(video);

                pipelineStateSupport.markAudioDone(pipelineState, audioObjectRef.objectKey(), "已写入本地音频缓存");
                stateStore.savePipelineState(bvid, pipelineState);
                return buildUpdates(savedVideo, audioObjectRef.provider(), audioObjectRef.objectKey(), transcript != null);
            } finally {
                Files.deleteIfExists(downloadedAudio.filePath());
            }
        } catch (Exception exception) {
            pipelineStateSupport.markAudioFailed(pipelineState, exception.getMessage());
            stateStore.savePipelineState(bvid, pipelineState);
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private Map<String, Object> buildUpdates(Video video, String provider, String objectKey, boolean transcriptPresent) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(IngestionState.VIDEO, video);
        updates.put(IngestionState.AUDIO_PATH, audioStorageService.resolvePath(provider, objectKey).toString());
        updates.put(IngestionState.TRANSCRIPT_PRESENT, transcriptPresent);
        return updates;
    }
}

