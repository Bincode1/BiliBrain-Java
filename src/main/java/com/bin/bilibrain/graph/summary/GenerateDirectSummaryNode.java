package com.bin.bilibrain.graph.summary;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.service.summary.SummaryGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerateDirectSummaryNode implements NodeAction {
    private final SummaryGenerationService summaryGenerationService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Video video = SummaryState.resolveVideo(state);
        String summaryText = summaryGenerationService.generateDirectSummary(video, SummaryState.transcriptText(state));
        Map<String, Object> updates = new HashMap<>();
        updates.put(SummaryState.SUMMARY_TEXT, summaryText);
        return updates;
    }
}
