package com.bin.bilibrain.bilibili;

import java.util.List;

public record BilibiliSubtitlePayload(
    Long cid,
    String sourceModel,
    String transcriptText,
    List<Segment> segments
) {
    public int segmentCount() {
        return segments == null ? 0 : segments.size();
    }

    public record Segment(
        double startSeconds,
        double endSeconds,
        String content
    ) {
    }
}
