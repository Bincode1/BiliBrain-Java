package com.bin.bilibrain.service.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoSummaryMapper;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.entity.VideoSummary;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoSummarySearchService {
    private final VideoSummaryMapper videoSummaryMapper;
    private final VideoMapper videoMapper;
    private final AppProperties appProperties;

    public List<ChatSourceVO> searchVideoSummaries(String query, Long folderId, String bvid) {
        List<VideoSummary> candidates = loadCandidates(folderId, bvid);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, Video> videos = videoMapper.selectBatchIds(
                candidates.stream().map(VideoSummary::getBvid).toList()
            ).stream()
            .collect(Collectors.toMap(Video::getBvid, Function.identity()));

        return candidates.stream()
            .map(summary -> toSource(summary, videos.get(summary.getBvid())))
            .filter(source -> matchesQuery(source, query))
            .sorted(Comparator.comparing(ChatSourceVO::videoTitle, String.CASE_INSENSITIVE_ORDER))
            .limit(appProperties.getRetrieval().getSearchTopK())
            .toList();
    }

    private List<VideoSummary> loadCandidates(Long folderId, String bvid) {
        if (StringUtils.hasText(bvid)) {
            VideoSummary summary = videoSummaryMapper.selectById(bvid.trim());
            return summary == null ? List.of() : List.of(summary);
        }

        List<VideoSummary> latestSummaries = videoSummaryMapper.selectList(
            new LambdaQueryWrapper<VideoSummary>()
                .orderByDesc(VideoSummary::getUpdatedAt)
                .last("LIMIT 50")
        );
        if (folderId == null) {
            return latestSummaries;
        }

        Set<String> folderVideoIds = videoMapper.selectList(
                new LambdaQueryWrapper<Video>()
                    .eq(Video::getFolderId, folderId)
                    .select(Video::getBvid)
            ).stream()
            .map(Video::getBvid)
            .collect(Collectors.toSet());

        return latestSummaries.stream()
            .filter(summary -> folderVideoIds.contains(summary.getBvid()))
            .toList();
    }

    private boolean matchesQuery(ChatSourceVO source, String query) {
        if (!StringUtils.hasText(query)) {
            return true;
        }
        String haystack = (safe(source.videoTitle()) + "\n" + safe(source.upName()) + "\n" + safe(source.excerpt()))
            .toLowerCase();
        return Arrays.stream(query.trim().toLowerCase().split("\\s+"))
            .filter(token -> !token.isBlank())
            .anyMatch(haystack::contains);
    }

    private ChatSourceVO toSource(VideoSummary summary, Video video) {
        return new ChatSourceVO(
            "summary",
            summary.getBvid(),
            video == null ? null : video.getFolderId(),
            video == null ? "" : safe(video.getTitle()),
            video == null ? "" : safe(video.getUpName()),
            null,
            null,
            excerpt(summary.getSummaryText())
        );
    }

    private String excerpt(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 260 ? normalized : normalized.substring(0, 260);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
