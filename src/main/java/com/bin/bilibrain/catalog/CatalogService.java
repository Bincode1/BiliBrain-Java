package com.bin.bilibrain.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bin.bilibrain.auth.AuthService;
import com.bin.bilibrain.auth.AuthSessionResponse;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.entity.Folder;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.ingestion.PipelineStatusService;
import com.bin.bilibrain.ingestion.VideoProcessSnapshot;
import com.bin.bilibrain.mapper.FolderMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

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
    private final AuthService authService;
    private final CatalogSyncService catalogSyncService;
    private final CatalogCacheService catalogCacheService;
    private final PipelineStatusService pipelineStatusService;

    public FolderListResponse listFolders(Long requestedUid) {
        Long effectiveUid = resolveRequestedUid(requestedUid);
        List<Folder> cachedFolders = loadFolders(effectiveUid);
        Optional<LocalDateTime> cachedAt = catalogCacheService.resolveFolderListCacheTime(effectiveUid, cachedFolders);
        boolean hasCache = !cachedFolders.isEmpty() || cachedAt.isPresent();

        if (!hasCache) {
            catalogSyncService.syncFolders(effectiveUid);
            List<FolderSummaryResponse> freshFolders = loadFolders(effectiveUid)
                .stream()
                .map(this::toFolderSummary)
                .toList();
            return new FolderListResponse(
                freshFolders,
                currentStatsForFolders(freshFolders),
                false,
                false
            );
        }

        boolean fresh = catalogCacheService.isFolderListFresh(cachedAt);
        if (!fresh) {
            catalogCacheService.scheduleFolderListRefresh(effectiveUid);
        }

        List<FolderSummaryResponse> folders = cachedFolders.stream()
            .map(this::toFolderSummary)
            .toList();

        return new FolderListResponse(
            folders,
            currentStatsForFolders(folders),
            true,
            !fresh
        );
    }

    public FolderVideosResponse listFolderVideos(Long folderId) {
        Folder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到这个收藏夹，请先创建或同步收藏夹。");
        }

        List<Video> videosInFolder = videoMapper.selectList(
            new LambdaQueryWrapper<Video>()
                .eq(Video::getFolderId, folderId)
                .orderByDesc(Video::getUpdatedAt)
                .orderByDesc(Video::getCreatedAt)
        );
        Optional<LocalDateTime> cachedAt = catalogCacheService.resolveFolderVideosCacheTime(folderId, videosInFolder);
        boolean hasCache = !videosInFolder.isEmpty() || cachedAt.isPresent();

        if (!hasCache) {
            catalogSyncService.syncFolderMetadata(folderId);
            videosInFolder = videoMapper.selectList(
                new LambdaQueryWrapper<Video>()
                    .eq(Video::getFolderId, folderId)
                    .orderByDesc(Video::getUpdatedAt)
                    .orderByDesc(Video::getCreatedAt)
            );
            return new FolderVideosResponse(
                toFolderSummary(folderMapper.selectById(folderId)),
                VIDEO_FIELDS,
                videosInFolder.stream().map(this::toVideoListItem).toList(),
                false,
                false
            );
        }

        boolean fresh = catalogCacheService.isFolderVideosFresh(cachedAt);
        if (!fresh) {
            catalogCacheService.scheduleFolderVideosRefresh(folderId);
        }

        List<VideoListItemResponse> videos = videosInFolder.stream()
            .map(this::toVideoListItem)
            .toList();

        return new FolderVideosResponse(
            toFolderSummary(folder),
            VIDEO_FIELDS,
            videos,
            true,
            !fresh
        );
    }

    private List<Folder> loadFolders(Long effectiveUid) {
        LambdaQueryWrapper<Folder> queryWrapper = new LambdaQueryWrapper<Folder>()
            .eq(Folder::getUid, effectiveUid)
            .orderByDesc(Folder::getMediaCount)
            .orderByDesc(Folder::getUpdatedAt);
        return folderMapper.selectList(queryWrapper);
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
        VideoProcessSnapshot snapshot = pipelineStatusService.getCatalogSnapshot(video);
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
            snapshot.manualTags(),
            defaultString(snapshot.transcriptSource()),
            snapshot.transcriptSegmentCount(),
            defaultString(snapshot.transcriptUpdatedAt()),
            defaultString(snapshot.syncStatus()),
            snapshot.chunkCount(),
            snapshot.hasSummary(),
            formatDateTime(video.getSyncedAt()),
            defaultString(snapshot.errorMsg()),
            isInvalid,
            formatDateTime(video.getCreatedAt()),
            snapshot.pipeline()
        );
    }

    private CatalogStatsResponse currentStatsForFolders(List<FolderSummaryResponse> folders) {
        long folderCount = folders.size();
        long videoCount = folders.isEmpty()
            ? 0L
            : videoMapper.selectCount(new LambdaQueryWrapper<Video>().in(
                Video::getFolderId,
                folders.stream().map(FolderSummaryResponse::folderId).toList()
            ));
        return new CatalogStatsResponse(folderCount, videoCount);
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
        AuthSessionResponse session = authService.getSession();
        if (session.loggedIn() && session.uid() != null && session.uid() > 0) {
            return session.uid();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先扫码登录 Bilibili，或提供 uid。");
    }
}
