package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.entity.Transcript;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.ingestion.PipelineStateSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ValidateVideoNode implements NodeAction {
    private final IngestionGraphStateStore stateStore;
    private final PipelineStateSupport pipelineStateSupport;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String bvid = IngestionState.requireBvid(state);
        Video video = IngestionState.resolveVideo(state);
        if (video == null) {
            video = stateStore.requireVideo(bvid);
        }

        int durationSeconds = state.value(IngestionState.DURATION_SECONDS, Integer.class)
            .orElse(video.getDuration() == null ? 0 : video.getDuration());
        int maxVideoMinutes = state.value(IngestionState.MAX_VIDEO_MINUTES, Integer.class).orElse(30);

        if (video.getIsInvalid() != null && video.getIsInvalid() != 0) {
            failValidation(bvid, video, "失效视频无法开始处理。");
        }
        if (durationSeconds > maxVideoMinutes * 60) {
            failValidation(bvid, video, "视频时长超过当前全局限制，请先调整配置后重试。");
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(IngestionState.VIDEO, video);
        updates.put(IngestionState.DURATION_SECONDS, durationSeconds);
        updates.put(IngestionState.MAX_VIDEO_MINUTES, maxVideoMinutes);
        return updates;
    }

    private void failValidation(String bvid, Video video, String message) {
        Transcript transcript = stateStore.findTranscript(bvid);
        Map<String, Map<String, Object>> pipelineState = stateStore.loadPipelineState(bvid, video, transcript);
        pipelineStateSupport.markAudioFailed(pipelineState, message);
        stateStore.savePipelineState(bvid, pipelineState);
        throw new IllegalStateException(message);
    }
}
