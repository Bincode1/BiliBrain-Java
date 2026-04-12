package com.bin.bilibrain.graph.summary;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class SummaryGraphConfig {

    @Bean("summaryStateGraph")
    public StateGraph summaryStateGraph(
        LoadSummaryContextNode loadSummaryContextNode,
        PrepareSummaryWindowsNode prepareSummaryWindowsNode,
        GenerateDirectSummaryNode generateDirectSummaryNode,
        GenerateWindowSummariesNode generateWindowSummariesNode,
        ReduceWindowSummariesNode reduceWindowSummariesNode,
        SaveSummaryNode saveSummaryNode
    ) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
            .defaultStrategy(new ReplaceStrategy())
            .build();

        return new StateGraph("bilibili-summary", keyStrategyFactory)
            .addNode("load_summary_context", node_async(loadSummaryContextNode))
            .addNode("prepare_summary_windows", node_async(prepareSummaryWindowsNode))
            .addNode("generate_direct_summary", node_async(generateDirectSummaryNode))
            .addNode("generate_window_summaries", node_async(generateWindowSummariesNode))
            .addNode("reduce_window_summaries", node_async(reduceWindowSummariesNode))
            .addNode("save_summary", node_async(saveSummaryNode))
            .addEdge(START, "load_summary_context")
            .addConditionalEdges(
                "load_summary_context",
                (AsyncEdgeAction) state -> CompletableFuture.completedFuture(
                    SummaryState.isCacheHit(state) ? "cached" : "summarize"
                ),
                Map.of(
                    "cached", END,
                    "summarize", "prepare_summary_windows"
                )
            )
            .addConditionalEdges(
                "prepare_summary_windows",
                (AsyncEdgeAction) state -> CompletableFuture.completedFuture(
                    SummaryState.MODE_WINDOWED.equals(state.value(SummaryState.SUMMARY_MODE, String.class).orElse(""))
                        ? "windowed"
                        : "direct"
                ),
                Map.of(
                    "direct", "generate_direct_summary",
                    "windowed", "generate_window_summaries"
                )
            )
            .addEdge("generate_direct_summary", "save_summary")
            .addEdge("generate_window_summaries", "reduce_window_summaries")
            .addEdge("reduce_window_summaries", "save_summary")
            .addEdge("save_summary", END);
    }

    @Bean("summaryCompiledGraph")
    public CompiledGraph summaryCompiledGraph(StateGraph summaryStateGraph) throws GraphStateException {
        return summaryStateGraph.compile(
            CompileConfig.builder()
                .recursionLimit(8)
                .build()
        );
    }
}
