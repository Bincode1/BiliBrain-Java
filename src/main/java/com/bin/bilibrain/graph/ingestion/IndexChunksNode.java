package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.service.ingestion.PipelineStateSupport;
import com.bin.bilibrain.service.retrieval.VectorSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IndexChunksNode implements NodeAction {
    private final IngestionGraphStateStore stateStore;
    private final PipelineStateSupport pipelineStateSupport;
    private final VectorSearchService vectorSearchService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String bvid = IngestionState.requireBvid(state);
        Video video = IngestionState.resolveVideo(state);
        if (video == null) {
            video = stateStore.requireVideo(bvid);
        }

        Transcript transcript = stateStore.findTranscript(bvid);
        Map<String, Map<String, Object>> pipelineState = stateStore.loadPipelineState(bvid, video, transcript);
        List<Document> chunkDocuments = IngestionState.resolveChunkDocuments(state);

        if (!vectorSearchService.isAvailable()) {
            pipelineStateSupport.markIndexPending(pipelineState, "向量索引未启用，当前仅保留转写结果");
            stateStore.savePipelineState(bvid, pipelineState);
            return buildUpdates(video, 0);
        }

        try {
            pipelineStateSupport.markIndexRunning(pipelineState, "正在写入 Milvus");
            stateStore.savePipelineState(bvid, pipelineState);
            vectorSearchService.deleteByBvid(bvid);
            vectorSearchService.addDocuments(chunkDocuments);
            pipelineStateSupport.markIndexDone(pipelineState, chunkDocuments.size(), "已写入 Milvus");
            stateStore.savePipelineState(bvid, pipelineState);
            return buildUpdates(video, chunkDocuments.size());
        } catch (Exception exception) {
            pipelineStateSupport.markIndexFailed(pipelineState, exception.getMessage());
            stateStore.savePipelineState(bvid, pipelineState);
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private Map<String, Object> buildUpdates(Video video, int chunkCount) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(IngestionState.VIDEO, video);
        updates.put(IngestionState.INDEXED_CHUNK_COUNT, chunkCount);
        return updates;
    }
}
