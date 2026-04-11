package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.entity.Transcript;
import com.bin.bilibrain.entity.Video;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FinalizePipelineNode implements NodeAction {
    private final IngestionGraphStateStore stateStore;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String bvid = IngestionState.requireBvid(state);
        Video video = IngestionState.resolveVideo(state);
        if (video == null) {
            video = stateStore.requireVideo(bvid);
        }

        Transcript transcript = stateStore.findTranscript(bvid);
        stateStore.savePipelineState(bvid, stateStore.loadPipelineState(bvid, video, transcript));
        stateStore.touchVideo(bvid);

        Map<String, Object> updates = new HashMap<>();
        updates.put(IngestionState.VIDEO, stateStore.requireVideo(bvid));
        updates.put(IngestionState.TRANSCRIPT_PRESENT, transcript != null);
        return updates;
    }
}
