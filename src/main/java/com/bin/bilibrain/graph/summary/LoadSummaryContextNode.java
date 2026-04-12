package com.bin.bilibrain.graph.summary;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.entity.VideoSummary;
import com.bin.bilibrain.service.summary.SummaryHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LoadSummaryContextNode implements NodeAction {
    private final SummaryGraphStateStore stateStore;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String bvid = SummaryState.requireBvid(state);
        Video video = stateStore.requireVideo(bvid);
        Transcript transcript = stateStore.requireTranscript(bvid);
        VideoSummary existingSummary = stateStore.findSummary(bvid);
        String transcriptHash = SummaryHashUtils.sha256(transcript.getTranscriptText());

        boolean cacheHit = existingSummary != null
            && StringUtils.hasText(existingSummary.getSummaryText())
            && transcriptHash.equals(existingSummary.getTranscriptHash());

        Map<String, Object> updates = new HashMap<>();
        updates.put(SummaryState.VIDEO, video);
        updates.put(SummaryState.TRANSCRIPT, transcript);
        updates.put(SummaryState.TRANSCRIPT_TEXT, transcript.getTranscriptText());
        updates.put(SummaryState.TRANSCRIPT_HASH, transcriptHash);
        updates.put(SummaryState.CACHE_HIT, cacheHit);
        if (existingSummary != null) {
            updates.put(SummaryState.SUMMARY, existingSummary);
        }
        return updates;
    }
}
