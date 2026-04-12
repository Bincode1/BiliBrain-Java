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
public class ReduceWindowSummariesNode implements NodeAction {
    private final SummaryGenerationService summaryGenerationService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        List<String> windowSummaries = SummaryState.resolveWindowSummaries(state);
        if (windowSummaries.isEmpty()) {
            throw new IllegalStateException("窗口摘要为空，无法归并最终摘要。");
        }

        String summaryText = windowSummaries.size() == 1
            ? windowSummaries.get(0)
            : summaryGenerationService.reduceWindowSummaries(SummaryState.resolveVideo(state), windowSummaries);

        Map<String, Object> updates = new HashMap<>();
        updates.put(SummaryState.SUMMARY_TEXT, summaryText);
        return updates;
    }
}
