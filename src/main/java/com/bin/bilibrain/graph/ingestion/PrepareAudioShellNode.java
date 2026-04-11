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
public class PrepareAudioShellNode implements NodeAction {
    private final IngestionGraphStateStore stateStore;
    private final PipelineStateSupport pipelineStateSupport;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String bvid = IngestionState.requireBvid(state);
        Video video = IngestionState.resolveVideo(state);
        if (video == null) {
            video = stateStore.requireVideo(bvid);
        }

        Transcript transcript = stateStore.findTranscript(bvid);
        Map<String, Map<String, Object>> pipelineState = stateStore.loadPipelineState(bvid, video, transcript);

        if ("pending".equals(String.valueOf(pipelineState.get("audio").get("status")))) {
            pipelineStateSupport.markAudioRunning(pipelineState);
            pipelineStateSupport.markAudioShellCompleted(pipelineState);
        }

        if (transcript != null && transcript.getTranscriptText() != null && !transcript.getTranscriptText().isBlank()) {
            pipelineStateSupport.markTranscriptDone(
                pipelineState,
                transcript.getSourceModel(),
                transcript.getSegmentCount() == null ? 0 : transcript.getSegmentCount()
            );
        } else if ("pending".equals(String.valueOf(pipelineState.get("transcript").get("status")))) {
            pipelineStateSupport.markTranscriptPending(pipelineState);
        }

        stateStore.savePipelineState(bvid, pipelineState);

        Map<String, Object> updates = new HashMap<>();
        updates.put(IngestionState.VIDEO, video);
        updates.put(IngestionState.TRANSCRIPT_PRESENT, transcript != null);
        return updates;
    }
}
