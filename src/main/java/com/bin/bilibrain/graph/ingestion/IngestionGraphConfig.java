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
        DownloadAudioNode downloadAudioNode,
        AsrTranscribeNode asrTranscribeNode,
        ChunkTranscriptNode chunkTranscriptNode,
        IndexChunksNode indexChunksNode,
        FinalizePipelineNode finalizePipelineNode
    ) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
            .defaultStrategy(new ReplaceStrategy())
            .build();

        return new StateGraph("bilibili-ingestion", keyStrategyFactory)
            .addNode("load_video_context", node_async(loadVideoContextNode))
            .addNode("validate_video", node_async(validateVideoNode))
            .addNode("download_audio", node_async(downloadAudioNode))
            .addNode("asr_transcribe", node_async(asrTranscribeNode))
            .addNode("chunk_transcript", node_async(chunkTranscriptNode))
            .addNode("index_chunks", node_async(indexChunksNode))
            .addNode("finalize_pipeline", node_async(finalizePipelineNode))
            .addEdge(START, "load_video_context")
            .addEdge("load_video_context", "validate_video")
            .addEdge("validate_video", "download_audio")
            .addEdge("download_audio", "asr_transcribe")
            .addEdge("asr_transcribe", "chunk_transcript")
            .addEdge("chunk_transcript", "index_chunks")
            .addEdge("index_chunks", "finalize_pipeline")
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
