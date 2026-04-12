package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.bin.bilibrain.model.entity.Video;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IngestionState {
    public static final String BVID = "bvid";
    public static final String VIDEO = "video";
    public static final String DURATION_SECONDS = "duration_seconds";
    public static final String MAX_VIDEO_MINUTES = "max_video_minutes";
    public static final String TRANSCRIPT_PRESENT = "transcript_present";
    public static final String AUDIO_PATH = "audio_path";
    public static final String CHUNK_DOCUMENTS = "chunk_documents";
    public static final String INDEXED_CHUNK_COUNT = "indexed_chunk_count";

    private IngestionState() {
    }

    public static Map<String, Object> initialInput(String bvid) {
        Map<String, Object> input = new HashMap<>();
        input.put(BVID, bvid);
        return input;
    }

    public static String requireBvid(OverAllState state) {
        return state.value(BVID, String.class)
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalStateException("ingestion graph 缺少 bvid。"));
    }

    public static Video resolveVideo(OverAllState state) {
        return state.value(VIDEO, Video.class).orElse(null);
    }

    public static String resolveAudioPath(OverAllState state) {
        return state.value(AUDIO_PATH, String.class).orElse("");
    }

    @SuppressWarnings("unchecked")
    public static List<Document> resolveChunkDocuments(OverAllState state) {
        return state.value(CHUNK_DOCUMENTS, List.class).orElse(List.of());
    }
}

