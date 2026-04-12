package com.bin.bilibrain.service.catalog;

import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.vo.catalog.VideoTagsVO;
import com.bin.bilibrain.model.vo.catalog.VideoTranscriptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoContentService {
    private final VideoMapper videoMapper;
    private final TranscriptMapper transcriptMapper;

    public VideoTranscriptVO getTranscript(String bvid) {
        Video video = requireVideo(bvid);
        Transcript transcript = transcriptMapper.findByBvid(bvid);
        if (transcript == null || !StringUtils.hasText(transcript.getTranscriptText())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "这个视频还没有转写，请先开始处理。", HttpStatus.CONFLICT);
        }
        return new VideoTranscriptVO(
            bvid,
            defaultTitle(video),
            blankToDefault(transcript.getSourceModel(), "未转写"),
            transcript.getSegmentCount() == null ? 0 : transcript.getSegmentCount(),
            transcript.getTranscriptText(),
            formatDateTime(transcript.getUpdatedAt()),
            true
        );
    }

    public VideoTagsVO updateTags(String bvid, List<String> tags) {
        Video video = requireVideo(bvid);
        String normalizedTags = normalizeTags(tags);
        video.setManualTags(normalizedTags);
        video.setUpdatedAt(LocalDateTime.now());
        videoMapper.updateById(video);
        return new VideoTagsVO(bvid, parseTags(normalizedTags));
    }

    private Video requireVideo(String bvid) {
        Video video = videoMapper.selectById(bvid);
        if (video == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "找不到这个视频，请先同步收藏夹元数据。", HttpStatus.NOT_FOUND);
        }
        return video;
    }

    private String normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }

    private List<String> parseTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return List.of(raw.split("[,，\\n]+")).stream()
            .map(String::trim)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    private String defaultTitle(Video video) {
        return StringUtils.hasText(video.getTitle()) ? video.getTitle() : video.getBvid();
    }

    private String blankToDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
