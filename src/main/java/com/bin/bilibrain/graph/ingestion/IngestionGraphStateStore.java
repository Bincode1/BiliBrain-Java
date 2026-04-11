package com.bin.bilibrain.graph.ingestion;

import com.bin.bilibrain.entity.Transcript;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.entity.VideoPipeline;
import com.bin.bilibrain.ingestion.PipelineStateSupport;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
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

    public void touchVideo(String bvid) {
        Video video = videoMapper.selectById(bvid);
        if (video == null) {
            return;
        }
        video.setUpdatedAt(LocalDateTime.now());
        videoMapper.updateById(video);
    }
}
