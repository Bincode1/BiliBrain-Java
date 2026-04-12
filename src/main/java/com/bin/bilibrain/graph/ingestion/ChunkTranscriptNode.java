package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.service.ingestion.PipelineStateSupport;
import com.bin.bilibrain.service.retrieval.TranscriptChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChunkTranscriptNode implements NodeAction {
    private final IngestionGraphStateStore stateStore;
    private final PipelineStateSupport pipelineStateSupport;
    private final TranscriptChunkService transcriptChunkService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String bvid = IngestionState.requireBvid(state);
        Video video = IngestionState.resolveVideo(state);
        if (video == null) {
            video = stateStore.requireVideo(bvid);
        }

        Transcript transcript = stateStore.findTranscript(bvid);
        Map<String, Map<String, Object>> pipelineState = stateStore.loadPipelineState(bvid, video, transcript);
        pipelineStateSupport.markIndexRunning(pipelineState, "正在切分转写内容");
        stateStore.savePipelineState(bvid, pipelineState);

        List<Document> chunkDocuments = transcriptChunkService.buildChunkDocuments(video, transcript);
        if (chunkDocuments.isEmpty()) {
            pipelineStateSupport.markIndexFailed(pipelineState, "当前转写内容为空，无法建立索引。");
            stateStore.savePipelineState(bvid, pipelineState);
            throw new IllegalStateException("当前转写内容为空，无法建立索引。");
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(IngestionState.VIDEO, video);
        updates.put(IngestionState.CHUNK_DOCUMENTS, chunkDocuments);
        updates.put(IngestionState.INDEXED_CHUNK_COUNT, chunkDocuments.size());
        return updates;
    }
}
