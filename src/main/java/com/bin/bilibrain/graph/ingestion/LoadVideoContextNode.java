package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.service.system.ProcessingSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LoadVideoContextNode implements NodeAction {
    private final IngestionGraphStateStore stateStore;
    private final ProcessingSettingsService processingSettingsService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String bvid = IngestionState.requireBvid(state);
        Video video = stateStore.requireVideo(bvid);
        int durationSeconds = video.getDuration() == null ? 0 : video.getDuration();

        Map<String, Object> updates = new HashMap<>();
        updates.put(IngestionState.BVID, bvid);
        updates.put(IngestionState.VIDEO, video);
        updates.put(IngestionState.DURATION_SECONDS, durationSeconds);
        updates.put(IngestionState.MAX_VIDEO_MINUTES, processingSettingsService.getSettings().maxVideoMinutes());
        return updates;
    }
}

