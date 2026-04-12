package com.bin.bilibrain.graph.summary;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.bin.bilibrain.model.entity.VideoSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SummaryGraphRunner {
    @Qualifier("summaryCompiledGraph")
    private final CompiledGraph summaryCompiledGraph;

    public SummaryRunResult run(String bvid) {
        var state = summaryCompiledGraph.invoke(SummaryState.initialInput(bvid))
            .orElseThrow(() -> new IllegalStateException("summary graph 未返回最终状态。"));
        VideoSummary summary = SummaryState.resolveSummary(state);
        if (summary == null) {
            throw new IllegalStateException("summary graph 未产出摘要结果。");
        }
        return new SummaryRunResult(summary, !SummaryState.isCacheHit(state));
    }

    public record SummaryRunResult(VideoSummary summary, boolean generated) {
    }
}
