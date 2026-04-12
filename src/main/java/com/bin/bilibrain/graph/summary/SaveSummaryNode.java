package com.bin.bilibrain.graph.summary;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.model.entity.VideoSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SaveSummaryNode implements NodeAction {
    private final SummaryGraphStateStore stateStore;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        VideoSummary summary = stateStore.saveSummary(
            SummaryState.requireBvid(state),
            SummaryState.transcriptHash(state),
            SummaryState.resolveSummaryText(state)
        );
        Map<String, Object> updates = new HashMap<>();
        updates.put(SummaryState.SUMMARY, summary);
        updates.put(SummaryState.CACHE_HIT, false);
        return updates;
    }
}
