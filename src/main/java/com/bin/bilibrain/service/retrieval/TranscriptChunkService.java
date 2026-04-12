package com.bin.bilibrain.service.retrieval;

import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TranscriptChunkService {
    private final DocumentSplitter documentSplitter;
    private final ObjectMapper objectMapper;

    public List<Document> buildChunkDocuments(Video video, Transcript transcript) {
        if (video == null || transcript == null || !StringUtils.hasText(transcript.getTranscriptText())) {
            return List.of();
        }

        List<DocumentSplitter.TranscriptSegment> segments = parseSegments(transcript);
        if (segments.isEmpty()) {
            segments = List.of(new DocumentSplitter.TranscriptSegment(0.0, 0.0, transcript.getTranscriptText()));
        }

        List<DocumentSplitter.TranscriptChunk> chunks = documentSplitter.splitSegments(segments);
        List<Document> documents = new ArrayList<>(chunks.size());
        for (DocumentSplitter.TranscriptChunk chunk : chunks) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("bvid", video.getBvid());
            metadata.put("folder_id", video.getFolderId());
            metadata.put("chunk_index", chunk.chunkIndex());
            metadata.put("start_seconds", chunk.startSeconds());
            metadata.put("end_seconds", chunk.endSeconds());
            metadata.put("video_title", valueOrEmpty(video.getTitle()));
            metadata.put("up_name", valueOrEmpty(video.getUpName()));
            metadata.put("source_kind", "chunk");
            documents.add(new Document(
                chunk.text(),
                video.getBvid() + "#chunk-" + chunk.chunkIndex(),
                metadata
            ));
        }
        return documents;
    }

    private List<DocumentSplitter.TranscriptSegment> parseSegments(Transcript transcript) {
        if (!StringUtils.hasText(transcript.getSegmentsJson())) {
            return List.of();
        }
        try {
            List<TranscriptSegmentPayload> payloads = objectMapper.readValue(
                transcript.getSegmentsJson(),
                new TypeReference<>() {
                }
            );
            return payloads.stream()
                .map(payload -> new DocumentSplitter.TranscriptSegment(
                    payload.startSeconds(),
                    payload.endSeconds(),
                    payload.content()
                ))
                .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("解析 transcript segments 失败。", exception);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record TranscriptSegmentPayload(
        int index,
        double startSeconds,
        double endSeconds,
        String content
    ) {
    }
}
