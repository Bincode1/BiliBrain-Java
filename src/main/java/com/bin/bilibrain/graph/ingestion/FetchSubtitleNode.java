package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.bilibili.BilibiliSubtitleClient;
import com.bin.bilibrain.bilibili.BilibiliSubtitlePayload;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.entity.Transcript;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.ingestion.PipelineStateSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FetchSubtitleNode implements NodeAction {
    private final IngestionGraphStateStore stateStore;
    private final PipelineStateSupport pipelineStateSupport;
    private final BilibiliSubtitleClient bilibiliSubtitleClient;
    private final AppProperties appProperties;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String bvid = IngestionState.requireBvid(state);
        Video video = IngestionState.resolveVideo(state);
        if (video == null) {
            video = stateStore.requireVideo(bvid);
        }

        Transcript existingTranscript = stateStore.findTranscript(bvid);
        Map<String, Map<String, Object>> pipelineState = stateStore.loadPipelineState(bvid, video, existingTranscript);
        if (hasTranscript(existingTranscript)) {
            if (isSubtitleTranscript(existingTranscript) && isAudioPending(pipelineState)) {
                pipelineStateSupport.markAudioDone(
                    pipelineState,
                    "subtitle://" + existingTranscript.getSourceModel(),
                    "已复用现有字幕转写"
                );
            }
            pipelineStateSupport.markTranscriptDone(
                pipelineState,
                existingTranscript.getSourceModel(),
                safeSegmentCount(existingTranscript)
            );
            stateStore.savePipelineState(bvid, pipelineState);
            return buildResult(video, true);
        }

        if (!appProperties.getProcessing().isSubtitleFirstEnabled()) {
            pipelineStateSupport.markTranscriptPending(pipelineState);
            pipelineStateSupport.markAudioShellCompleted(pipelineState);
            stateStore.savePipelineState(bvid, pipelineState);
            return buildResult(video, false);
        }

        pipelineStateSupport.markTranscriptRunning(pipelineState, "bilibili-cc", "正在尝试获取 B 站字幕");
        stateStore.savePipelineState(bvid, pipelineState);

        Optional<BilibiliSubtitlePayload> subtitlePayload = fetchSubtitleSafely(bvid, video.getCid());
        if (subtitlePayload.isPresent()) {
            Transcript savedTranscript = stateStore.saveSubtitleTranscript(video, subtitlePayload.get());
            Video refreshedVideo = stateStore.requireVideo(bvid);
            Map<String, Map<String, Object>> refreshedState = stateStore.loadPipelineState(bvid, refreshedVideo, savedTranscript);
            pipelineStateSupport.markAudioDone(
                refreshedState,
                "subtitle://" + subtitlePayload.get().sourceModel(),
                "已使用 B 站字幕，跳过音频下载"
            );
            pipelineStateSupport.markTranscriptDone(
                refreshedState,
                subtitlePayload.get().sourceModel(),
                subtitlePayload.get().segmentCount()
            );
            stateStore.savePipelineState(bvid, refreshedState);
            return buildResult(refreshedVideo, true);
        }

        pipelineStateSupport.markTranscriptPending(pipelineState);
        pipelineStateSupport.markAudioShellCompleted(pipelineState);
        stateStore.savePipelineState(bvid, pipelineState);
        return buildResult(video, false);
    }

    private Optional<BilibiliSubtitlePayload> fetchSubtitleSafely(String bvid, Long cid) {
        try {
            return bilibiliSubtitleClient.fetchSubtitle(bvid, cid);
        } catch (Exception exception) {
            log.warn("subtitle-first fetch failed for {}, fallback to shell audio stage", bvid, exception);
            return Optional.empty();
        }
    }

    private boolean hasTranscript(Transcript transcript) {
        return transcript != null
            && transcript.getTranscriptText() != null
            && !transcript.getTranscriptText().isBlank();
    }

    private boolean isSubtitleTranscript(Transcript transcript) {
        return transcript != null
            && transcript.getSourceModel() != null
            && transcript.getSourceModel().startsWith("bilibili");
    }

    private boolean isAudioPending(Map<String, Map<String, Object>> pipelineState) {
        return "pending".equals(String.valueOf(pipelineState.get("audio").get("status")));
    }

    private int safeSegmentCount(Transcript transcript) {
        return transcript.getSegmentCount() == null ? 0 : transcript.getSegmentCount();
    }

    private Map<String, Object> buildResult(Video video, boolean transcriptPresent) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(IngestionState.VIDEO, video);
        updates.put(IngestionState.TRANSCRIPT_PRESENT, transcriptPresent);
        return updates;
    }
}
