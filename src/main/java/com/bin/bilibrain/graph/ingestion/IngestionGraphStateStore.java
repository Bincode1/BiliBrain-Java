package com.bin.bilibrain.graph.ingestion;

import com.bin.bilibrain.bilibili.BilibiliSubtitlePayload;
import com.bin.bilibrain.entity.Transcript;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.entity.VideoPipeline;
import com.bin.bilibrain.ingestion.PipelineStateSupport;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IngestionGraphStateStore {
    private final VideoMapper videoMapper;
    private final TranscriptMapper transcriptMapper;
    private final VideoPipelineMapper videoPipelineMapper;
    private final PipelineStateSupport pipelineStateSupport;
    private final ObjectMapper objectMapper;

    public Video requireVideo(String bvid) {
        Video video = videoMapper.selectById(bvid);
        if (video == null) {
            throw new IllegalStateException("视频不存在，无法开始处理。");
        }
        return video;
    }

    public Transcript findTranscript(String bvid) {
        return transcriptMapper.findByBvid(bvid);
    }

    public Map<String, Map<String, Object>> loadPipelineState(String bvid, Video video, Transcript transcript) {
        VideoPipeline existing = videoPipelineMapper.selectById(bvid);
        Map<String, Map<String, Object>> state = pipelineStateSupport.readState(existing == null ? null : existing.getStateJson());
        pipelineStateSupport.hydrateAudioStep(video != null ? video : requireVideo(bvid), state);
        pipelineStateSupport.hydrateTranscriptStep(
            transcript != null ? transcript : transcriptMapper.findByBvid(bvid),
            state
        );
        return state;
    }

    public void savePipelineState(String bvid, Map<String, Map<String, Object>> state) {
        LocalDateTime now = LocalDateTime.now();
        String overallStatus = pipelineStateSupport.overallStatus(state);
        String stateJson = pipelineStateSupport.writeState(state);
        VideoPipeline existing = videoPipelineMapper.selectById(bvid);
        if (existing == null) {
            videoPipelineMapper.insert(VideoPipeline.builder()
                .bvid(bvid)
                .overallStatus(overallStatus)
                .stateJson(stateJson)
                .updatedAt(now)
                .build());
            return;
        }
        existing.setOverallStatus(overallStatus);
        existing.setStateJson(stateJson);
        existing.setUpdatedAt(now);
        videoPipelineMapper.updateById(existing);
    }

    public Transcript saveSubtitleTranscript(Video video, BilibiliSubtitlePayload subtitlePayload) {
        LocalDateTime now = LocalDateTime.now();
        Transcript existing = transcriptMapper.findByBvid(video.getBvid());
        String segmentsJson = toJson(subtitlePayload.segments());
        if (existing == null) {
            transcriptMapper.insert(Transcript.builder()
                .bvid(video.getBvid())
                .sourceModel(subtitlePayload.sourceModel())
                .segmentCount(subtitlePayload.segmentCount())
                .transcriptText(subtitlePayload.transcriptText())
                .segmentsJson(segmentsJson)
                .createdAt(now)
                .updatedAt(now)
                .build());
        } else {
            existing.setSourceModel(subtitlePayload.sourceModel());
            existing.setSegmentCount(subtitlePayload.segmentCount());
            existing.setTranscriptText(subtitlePayload.transcriptText());
            existing.setSegmentsJson(segmentsJson);
            existing.setUpdatedAt(now);
            transcriptMapper.updateById(existing);
        }

        Video freshVideo = videoMapper.selectById(video.getBvid());
        if (freshVideo != null) {
            if (subtitlePayload.cid() != null && subtitlePayload.cid() > 0) {
                freshVideo.setCid(subtitlePayload.cid());
            }
            freshVideo.setSubtitleSource(subtitlePayload.sourceModel());
            freshVideo.setSyncedAt(now);
            freshVideo.setUpdatedAt(now);
            videoMapper.updateById(freshVideo);
        }

        return transcriptMapper.findByBvid(video.getBvid());
    }

    public void touchVideo(String bvid) {
        Video video = videoMapper.selectById(bvid);
        if (video == null) {
            return;
        }
        video.setUpdatedAt(LocalDateTime.now());
        videoMapper.updateById(video);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("写入字幕 segments_json 失败。", exception);
        }
    }
}
