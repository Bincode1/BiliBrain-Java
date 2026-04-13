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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SummaryService {
    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

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
        Video video = requireVideo(bvid);
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

        long startedAt = System.nanoTime();
        try {
            log.info(
                "summary generation started for bvid={} title={} transcriptChars={}",
                video.getBvid(),
                video.getTitle(),
                transcript.getTranscriptText().length()
            );
            SummaryGraphRunner.SummaryRunResult result = summaryGraphRunner.run(bvid);
            Transcript latestTranscript = transcriptMapper.findByBvid(bvid);
            log.info(
                "summary generation finished for bvid={} title={} generated={} costMs={}",
                video.getBvid(),
                video.getTitle(),
                result.generated(),
                (System.nanoTime() - startedAt) / 1_000_000L
            );
            return toVO(video, latestTranscript, result.summary(), result.generated());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn(
                "summary generation failed for bvid={} title={} costMs={} reason={}",
                video.getBvid(),
                video.getTitle(),
                (System.nanoTime() - startedAt) / 1_000_000L,
                exception.getMessage(),
                exception
            );
            throw new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "摘要生成失败：" + safeMessage(exception),
                HttpStatus.BAD_GATEWAY
            );
        }
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

    private String safeMessage(Exception exception) {
        return exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
            ? "模型未返回可用结果，请稍后重试。"
            : exception.getMessage();
    }
}
