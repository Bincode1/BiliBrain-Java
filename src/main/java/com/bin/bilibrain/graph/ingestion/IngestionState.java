package com.bin.bilibrain.graph.ingestion;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.bin.bilibrain.entity.Video;

import java.util.HashMap;
import java.util.Map;

public final class IngestionState {
    public static final String BVID = "bvid";
    public static final String VIDEO = "video";
    public static final String DURATION_SECONDS = "duration_seconds";
    public static final String MAX_VIDEO_MINUTES = "max_video_minutes";
    public static final String TRANSCRIPT_PRESENT = "transcript_present";

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
}
