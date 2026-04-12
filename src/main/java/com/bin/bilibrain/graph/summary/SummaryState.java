package com.bin.bilibrain.graph.summary;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.entity.VideoSummary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SummaryState {
    public static final String BVID = "bvid";
    public static final String VIDEO = "video";
    public static final String TRANSCRIPT = "transcript";
    public static final String SUMMARY = "summary";
    public static final String CACHE_HIT = "cache_hit";
    public static final String TRANSCRIPT_HASH = "transcript_hash";
    public static final String TRANSCRIPT_TEXT = "transcript_text";
    public static final String WINDOWS = "windows";
    public static final String WINDOW_SUMMARIES = "window_summaries";
    public static final String SUMMARY_TEXT = "summary_text";
    public static final String SUMMARY_MODE = "summary_mode";

    public static final String MODE_DIRECT = "direct";
    public static final String MODE_WINDOWED = "windowed";

    private SummaryState() {
    }

    public static Map<String, Object> initialInput(String bvid) {
        Map<String, Object> input = new HashMap<>();
        input.put(BVID, bvid);
        return input;
    }

    public static String requireBvid(OverAllState state) {
        return state.value(BVID, String.class)
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalStateException("summary graph 缺少 bvid。"));
    }

    public static Video resolveVideo(OverAllState state) {
        return state.value(VIDEO, Video.class).orElse(null);
    }

    public static Transcript resolveTranscript(OverAllState state) {
        return state.value(TRANSCRIPT, Transcript.class).orElse(null);
    }

    public static VideoSummary resolveSummary(OverAllState state) {
        return state.value(SUMMARY, VideoSummary.class).orElse(null);
    }

    public static boolean isCacheHit(OverAllState state) {
        return state.value(CACHE_HIT, Boolean.class).orElse(false);
    }

    public static String transcriptHash(OverAllState state) {
        return state.value(TRANSCRIPT_HASH, String.class).orElse("");
    }

    public static String transcriptText(OverAllState state) {
        return state.value(TRANSCRIPT_TEXT, String.class).orElse("");
    }

    @SuppressWarnings("unchecked")
    public static List<String> resolveWindows(OverAllState state) {
        return state.value(WINDOWS, List.class).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public static List<String> resolveWindowSummaries(OverAllState state) {
        return state.value(WINDOW_SUMMARIES, List.class).orElse(List.of());
    }

    public static String resolveSummaryText(OverAllState state) {
        return state.value(SUMMARY_TEXT, String.class).orElse("");
    }
}
