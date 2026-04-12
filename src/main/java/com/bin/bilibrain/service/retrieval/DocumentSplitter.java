package com.bin.bilibrain.service.retrieval;

import com.bin.bilibrain.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentSplitter {
    private final AppProperties appProperties;

    public DocumentSplitter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public List<TranscriptChunk> splitSegments(List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        int chunkSize = appProperties.getRetrieval().getChunkSize();
        int chunkOverlap = appProperties.getRetrieval().getChunkOverlap();
        List<TranscriptChunk> chunks = new ArrayList<>();

        int startIndex = 0;
        while (startIndex < segments.size()) {
            int endExclusive = startIndex;
            int currentLength = 0;
            while (endExclusive < segments.size()) {
                String segmentText = normalizeSegmentText(segments.get(endExclusive).content());
                if (!segmentText.isEmpty()) {
                    currentLength += segmentText.length();
                    if (endExclusive > startIndex) {
                        currentLength += 2;
                    }
                }
                endExclusive++;
                if (currentLength >= chunkSize) {
                    break;
                }
            }

            if (endExclusive <= startIndex) {
                endExclusive = startIndex + 1;
            }

            List<TranscriptSegment> chunkSegments = segments.subList(startIndex, endExclusive);
            chunks.add(new TranscriptChunk(
                chunks.size(),
                chunkSegments.getFirst().startSeconds(),
                chunkSegments.getLast().endSeconds(),
                joinSegments(chunkSegments)
            ));

            if (endExclusive >= segments.size()) {
                break;
            }

            int overlapLength = 0;
            int nextStart = endExclusive;
            while (nextStart > startIndex) {
                String segmentText = normalizeSegmentText(segments.get(nextStart - 1).content());
                overlapLength += segmentText.length();
                if (nextStart - 1 > startIndex) {
                    overlapLength += 2;
                }
                if (overlapLength >= chunkOverlap) {
                    nextStart -= 1;
                    break;
                }
                nextStart -= 1;
            }

            startIndex = Math.max(nextStart, startIndex + 1);
        }

        return chunks;
    }

    private String joinSegments(List<TranscriptSegment> segments) {
        return segments.stream()
            .map(TranscriptSegment::content)
            .map(this::normalizeSegmentText)
            .filter(StringUtils::hasText)
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse("");
    }

    private String normalizeSegmentText(String content) {
        return content == null ? "" : content.trim();
    }

    public record TranscriptSegment(
        double startSeconds,
        double endSeconds,
        String content
    ) {
    }

    public record TranscriptChunk(
        int chunkIndex,
        double startSeconds,
        double endSeconds,
        String text
    ) {
    }
}
