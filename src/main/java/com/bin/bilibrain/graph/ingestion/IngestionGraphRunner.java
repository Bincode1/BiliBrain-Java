package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class IngestionGraphRunner {
    private final CompiledGraph ingestionCompiledGraph;

    public IngestionGraphRunner(@Qualifier("ingestionCompiledGraph") CompiledGraph ingestionCompiledGraph) {
        this.ingestionCompiledGraph = ingestionCompiledGraph;
    }

    public void run(String bvid) {
        ingestionCompiledGraph.invoke(IngestionState.initialInput(bvid))
            .orElseThrow(() -> new IllegalStateException("ingestion graph 未返回最终状态。"));
    }
}
