package com.bin.bilibrain.graph.summary;

import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoSummaryMapper;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.entity.VideoSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SummaryGraphStateStore {
    private final VideoMapper videoMapper;
    private final TranscriptMapper transcriptMapper;
    private final VideoSummaryMapper videoSummaryMapper;

    public Video requireVideo(String bvid) {
        Video video = videoMapper.selectById(bvid);
        if (video == null) {
            throw new IllegalStateException("视频不存在，无法生成摘要。");
        }
        return video;
    }

    public Transcript requireTranscript(String bvid) {
        Transcript transcript = transcriptMapper.findByBvid(bvid);
        if (transcript == null || transcript.getTranscriptText() == null || transcript.getTranscriptText().isBlank()) {
            throw new IllegalStateException("当前视频还没有转写结果，暂时无法生成摘要。");
        }
        return transcript;
    }

    public VideoSummary findSummary(String bvid) {
        return videoSummaryMapper.selectById(bvid);
    }

    public VideoSummary saveSummary(String bvid, String transcriptHash, String summaryText) {
        LocalDateTime now = LocalDateTime.now();
        VideoSummary summary = VideoSummary.builder()
            .bvid(bvid)
            .transcriptHash(transcriptHash)
            .summaryText(summaryText)
            .updatedAt(now)
            .build();
        if (videoSummaryMapper.selectById(bvid) == null) {
            videoSummaryMapper.insert(summary);
        } else {
            videoSummaryMapper.updateById(summary);
        }
        return videoSummaryMapper.selectById(bvid);
    }
}
