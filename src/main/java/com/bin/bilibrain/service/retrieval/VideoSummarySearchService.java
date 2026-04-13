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
            .sorted(Comparator
                .comparingInt((ChatSourceVO source) -> matchScore(source, query))
                .reversed()
                .thenComparing(ChatSourceVO::videoTitle, String.CASE_INSENSITIVE_ORDER))
            .limit(resolveLimit(candidates.size(), query))
            .toList();
    }

    private List<VideoSummary> loadCandidates(Long folderId, String bvid) {
        if (StringUtils.hasText(bvid)) {
            VideoSummary summary = videoSummaryMapper.selectById(bvid.trim());
            return summary == null ? List.of() : List.of(summary);
        }

        if (folderId == null) {
            return videoSummaryMapper.selectList(
                new LambdaQueryWrapper<VideoSummary>()
                    .orderByDesc(VideoSummary::getUpdatedAt)
                    .last("LIMIT 50")
            );
        }

        Set<String> folderVideoIds = videoMapper.selectList(
                new LambdaQueryWrapper<Video>()
                    .eq(Video::getFolderId, folderId)
                    .select(Video::getBvid)
            ).stream()
            .map(Video::getBvid)
            .collect(Collectors.toSet());

        if (folderVideoIds.isEmpty()) {
            return List.of();
        }

        return videoSummaryMapper.selectBatchIds(folderVideoIds).stream()
            .filter(summary -> summary != null && StringUtils.hasText(summary.getSummaryText()))
            .sorted(Comparator.comparing(VideoSummary::getUpdatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
            .toList();
    }

    private int matchScore(ChatSourceVO source, String query) {
        String haystack = (safe(source.videoTitle()) + "\n" + safe(source.upName()) + "\n" + safe(source.excerpt()))
            .toLowerCase();
        if (!StringUtils.hasText(query)) {
            return 0;
        }
        String normalizedQuery = query.trim().toLowerCase();
        int score = 0;
        if (haystack.contains(normalizedQuery)) {
            score += 5;
        }
        for (String token : summaryTokens(normalizedQuery)) {
            if (!token.isBlank() && haystack.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private int resolveLimit(int candidateCount, String query) {
        if (isBroadSummaryQuery(query)) {
            return candidateCount;
        }
        return appProperties.getRetrieval().getSearchTopK();
    }

    private boolean isBroadSummaryQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return true;
        }
        String normalized = query.trim().toLowerCase();
        return normalized.contains("总结")
            || normalized.contains("概括")
            || normalized.contains("归纳")
            || normalized.contains("梳理")
            || normalized.contains("收藏夹")
            || normalized.contains("整体")
            || normalized.contains("全部")
            || normalized.contains("这一组")
            || normalized.contains("这组");
    }

    private List<String> summaryTokens(String normalizedQuery) {
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        for (String token : normalizedQuery.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        String compact = normalizedQuery.replace(" ", "");
        if (compact.length() >= 2) {
            tokens.add(compact);
        }
        List<String> keywords = List.of("总结", "概括", "归纳", "梳理", "收藏夹", "视频", "整体", "全部");
        for (String keyword : keywords) {
            if (compact.contains(keyword)) {
                tokens.add(keyword);
            }
        }
        return List.copyOf(tokens);
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
