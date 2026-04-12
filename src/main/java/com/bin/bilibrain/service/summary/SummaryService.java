package com.bin.bilibrain.service.summary;

import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.graph.summary.SummaryGraphRunner;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoSummaryMapper;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.entity.VideoSummary;
import com.bin.bilibrain.model.vo.summary.VideoSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SummaryService {
    private final VideoMapper videoMapper;
    private final TranscriptMapper transcriptMapper;
    private final VideoSummaryMapper videoSummaryMapper;
    private final SummaryGraphRunner summaryGraphRunner;
    private final SummaryGenerationService summaryGenerationService;

    public VideoSummaryVO getSummary(String bvid) {
        Video video = requireVideo(bvid);
        Transcript transcript = transcriptMapper.findByBvid(bvid);
        VideoSummary summary = videoSummaryMapper.selectById(bvid);
        return toVO(video, transcript, summary, false);
    }

    public VideoSummaryVO generateSummary(String bvid) {
        requireVideo(bvid);
        Transcript transcript = transcriptMapper.findByBvid(bvid);
        if (transcript == null || transcript.getTranscriptText() == null || transcript.getTranscriptText().isBlank()) {
            throw new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "当前视频还没有转写结果，暂时无法生成摘要。",
                HttpStatus.CONFLICT
            );
        }
        if (!summaryGenerationService.isAvailable()) {
            throw new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "摘要模型未启用，请先配置 DashScope Chat。",
                HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        SummaryGraphRunner.SummaryRunResult result = summaryGraphRunner.run(bvid);
        Transcript latestTranscript = transcriptMapper.findByBvid(bvid);
        return toVO(requireVideo(bvid), latestTranscript, result.summary(), result.generated());
    }

    private Video requireVideo(String bvid) {
        Video video = videoMapper.selectById(bvid);
        if (video == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "找不到这个视频。", HttpStatus.NOT_FOUND);
        }
        return video;
    }

    private VideoSummaryVO toVO(Video video, Transcript transcript, VideoSummary summary, boolean generated) {
        String transcriptHash = transcript == null ? "" : SummaryHashUtils.sha256(transcript.getTranscriptText());
        boolean available = summary != null && summary.getSummaryText() != null && !summary.getSummaryText().isBlank();
        boolean upToDate = available && transcriptHash.equals(summary.getTranscriptHash());

        return new VideoSummaryVO(
            video.getBvid(),
            available,
            upToDate,
            generated,
            available ? summary.getTranscriptHash() : "",
            available ? summary.getSummaryText() : "",
            available ? formatDateTime(summary.getUpdatedAt()) : ""
        );
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
