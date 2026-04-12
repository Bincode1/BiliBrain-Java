package com.bin.bilibrain.graph.summary;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.service.summary.SummaryGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerateWindowSummariesNode implements NodeAction {
    private final SummaryGenerationService summaryGenerationService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Video video = SummaryState.resolveVideo(state);
        List<String> windowSummaries = summaryGenerationService.generateWindowSummaries(
            video,
            SummaryState.resolveWindows(state)
        );
        Map<String, Object> updates = new HashMap<>();
        updates.put(SummaryState.WINDOW_SUMMARIES, windowSummaries);
        return updates;
    }
}
