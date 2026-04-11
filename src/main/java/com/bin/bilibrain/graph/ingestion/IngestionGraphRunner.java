package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestionGraphRunner {
    @Qualifier("ingestionCompiledGraph")
    private final CompiledGraph ingestionCompiledGraph;

    public void run(String bvid) {
        ingestionCompiledGraph.invoke(IngestionState.initialInput(bvid))
            .orElseThrow(() -> new IllegalStateException("ingestion graph 未返回最终状态。"));
    }
}
