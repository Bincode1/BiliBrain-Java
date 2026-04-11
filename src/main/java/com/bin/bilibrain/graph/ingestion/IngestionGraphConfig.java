package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class IngestionGraphConfig {

    @Bean("ingestionStateGraph")
    public StateGraph ingestionStateGraph(
        LoadVideoContextNode loadVideoContextNode,
        ValidateVideoNode validateVideoNode,
        FetchSubtitleNode fetchSubtitleNode,
        FinalizePipelineNode finalizePipelineNode
    ) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
            .defaultStrategy(new ReplaceStrategy())
            .build();

        return new StateGraph("bilibili-ingestion", keyStrategyFactory)
            .addNode("load_video_context", node_async(loadVideoContextNode))
            .addNode("validate_video", node_async(validateVideoNode))
            .addNode("fetch_subtitle", node_async(fetchSubtitleNode))
            .addNode("finalize_pipeline", node_async(finalizePipelineNode))
            .addEdge(START, "load_video_context")
            .addEdge("load_video_context", "validate_video")
            .addEdge("validate_video", "fetch_subtitle")
            .addEdge("fetch_subtitle", "finalize_pipeline")
            .addEdge("finalize_pipeline", END);
    }

    @Bean("ingestionCompiledGraph")
    public CompiledGraph ingestionCompiledGraph(StateGraph ingestionStateGraph) throws GraphStateException {
        return ingestionStateGraph.compile(
            CompileConfig.builder()
                .recursionLimit(8)
                .build()
        );
    }
}
