package com.bin.bilibrain.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.entity.Folder;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.mapper.FolderMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private static final List<String> VIDEO_FIELDS = List.of(
        "bvid",
        "title",
        "up_name",
        "duration",
        "published_at",
        "cover_url",
        "cid",
        "manual_tags",
        "transcript_source",
        "transcript_segment_count",
        "transcript_updated_at",
        "sync_status",
        "chunk_count",
        "pipeline",
        "synced_at",
        "error_msg"
    );

    private final FolderMapper folderMapper;
    private final VideoMapper videoMapper;
    private final AppProperties appProperties;

    public FolderListResponse listFolders(Long requestedUid) {
        LambdaQueryWrapper<Folder> queryWrapper = new LambdaQueryWrapper<Folder>()
            .orderByDesc(Folder::getMediaCount)
            .orderByDesc(Folder::getUpdatedAt);
        Long effectiveUid = resolveRequestedUid(requestedUid);
        if (effectiveUid != null && effectiveUid > 0) {
            queryWrapper.eq(Folder::getUid, effectiveUid);
        }

        List<FolderSummaryResponse> folders = folderMapper.selectList(queryWrapper)
            .stream()
            .map(this::toFolderSummary)
            .toList();

        long folderCount = folders.size();
        long videoCount;
        if (effectiveUid != null && effectiveUid > 0 && !folders.isEmpty()) {
            List<Long> folderIds = folders.stream()
                .map(FolderSummaryResponse::folderId)
                .toList();
            videoCount = videoMapper.selectCount(
                new LambdaQueryWrapper<Video>().in(Video::getFolderId, folderIds)
            );
        } else if (effectiveUid != null && effectiveUid > 0) {
            videoCount = 0L;
        } else {
            videoCount = videoMapper.selectCount(null);
        }
        return new FolderListResponse(
            folders,
            new CatalogStatsResponse(folderCount, videoCount),
            !folders.isEmpty(),
            false
        );
    }

    public FolderVideosResponse listFolderVideos(Long folderId) {
        Folder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到这个收藏夹，请先创建或同步收藏夹。");
        }

        List<VideoListItemResponse> videos = videoMapper.selectList(
                new LambdaQueryWrapper<Video>()
                    .eq(Video::getFolderId, folderId)
                    .orderByDesc(Video::getUpdatedAt)
                    .orderByDesc(Video::getCreatedAt)
            )
            .stream()
            .map(this::toVideoListItem)
            .toList();

        return new FolderVideosResponse(
            toFolderSummary(folder),
            VIDEO_FIELDS,
            videos,
            !videos.isEmpty(),
            false
        );
    }

    private FolderSummaryResponse toFolderSummary(Folder folder) {
        return new FolderSummaryResponse(
            folder.getFolderId(),
            folder.getTitle(),
            folder.getMediaCount(),
            formatDateTime(folder.getUpdatedAt())
        );
    }

    private VideoListItemResponse toVideoListItem(Video video) {
        String syncStatus = (video.getSyncedAt() == null) ? "pending" : "indexed";
        boolean isInvalid = video.getIsInvalid() != null && video.getIsInvalid() != 0;
        return new VideoListItemResponse(
            video.getBvid(),
            video.getFolderId(),
            defaultString(video.getTitle()),
            defaultString(video.getUpName()),
            defaultString(video.getCoverUrl()),
            valueOrZero(video.getDuration()),
            formatDateTime(video.getPublishedAt()),
            video.getCid(),
            List.of(),
            "未转写",
            0,
            "",
            syncStatus,
            0,
            false,
            formatDateTime(video.getSyncedAt()),
            "",
            isInvalid,
            formatDateTime(video.getCreatedAt()),
            defaultPipeline()
        );
    }

    private VideoPipelineResponse defaultPipeline() {
        return new VideoPipelineResponse(
            new VideoPipelineStepResponse("pending", "", "", "", 0, 0),
            new VideoPipelineStepResponse("pending", "", "", "", 0, 0),
            new VideoPipelineStepResponse("pending", "", "", "", 0, 0)
        );
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private Long resolveRequestedUid(Long requestedUid) {
        if (requestedUid != null && requestedUid > 0) {
            return requestedUid;
        }
        Long configuredUid = appProperties.getBilibili().getUid();
        if (configuredUid != null && configuredUid > 0) {
            return configuredUid;
        }
        return null;
    }
}
