package com.bin.bilibrain.service.catalog;

import com.bin.bilibrain.bilibili.BilibiliClientException;
import com.bin.bilibrain.bilibili.BilibiliFolderMetadata;
import com.bin.bilibrain.bilibili.BilibiliMetadataClient;
import com.bin.bilibrain.bilibili.BilibiliVideoMetadata;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.model.entity.Folder;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.mapper.FolderMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.model.vo.auth.AuthSessionVO;
import com.bin.bilibrain.model.vo.catalog.CatalogStatsResponse;
import com.bin.bilibrain.model.vo.catalog.FolderSyncResponse;
import com.bin.bilibrain.model.vo.catalog.FolderVideoSyncResponse;
import com.bin.bilibrain.model.vo.catalog.SyncErrorItem;
import com.bin.bilibrain.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogSyncService {
    private final BilibiliMetadataClient bilibiliMetadataClient;
    private final FolderMapper folderMapper;
    private final VideoMapper videoMapper;
    private final AppProperties appProperties;
    private final AuthService authService;
    private final CatalogCacheService catalogCacheService;

    @Transactional
    public FolderSyncResponse syncFolders(Long requestedUid) {
        long uid = resolveUid(requestedUid);
        try {
            List<BilibiliFolderMetadata> folders = bilibiliMetadataClient.listFolders(uid);
            int newFolders = 0;
            int updatedFolders = 0;
            for (BilibiliFolderMetadata folder : folders) {
                if (upsertFolder(uid, folder)) {
                    newFolders += 1;
                } else {
                    updatedFolders += 1;
                }
            }

            List<String> logs = List.of(
                "发现 " + folders.size() + " 个收藏夹。",
                "新增 " + newFolders + " 个收藏夹，更新 " + updatedFolders + " 个收藏夹元数据。"
            );
            catalogCacheService.markFolderListRefreshed(uid);
            return new FolderSyncResponse(uid, newFolders, updatedFolders, logs, currentStats());
        } catch (BilibiliClientException exception) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, exception.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @Transactional
    public FolderVideoSyncResponse syncFolderMetadata(Long folderId) {
        Folder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "找不到这个收藏夹，请先同步收藏夹列表。", HttpStatus.NOT_FOUND);
        }

        try {
            List<BilibiliVideoMetadata> remoteVideos = bilibiliMetadataClient.listFolderVideos(folderId);
            int newVideos = 0;
            int updatedVideos = 0;
            int failedVideos = 0;
            List<SyncErrorItem> errors = new ArrayList<>();
            for (BilibiliVideoMetadata remoteVideo : remoteVideos) {
                try {
                    if (upsertVideo(folderId, remoteVideo)) {
                        newVideos += 1;
                    } else {
                        updatedVideos += 1;
                    }
                } catch (Exception exception) {
                    failedVideos += 1;
                    errors.add(new SyncErrorItem(remoteVideo.bvid(), remoteVideo.title(), exception.getMessage()));
                }
            }

            List<String> logs = new ArrayList<>();
            logs.add("发现 " + remoteVideos.size() + " 个视频。");
            logs.add("新增 " + newVideos + " 个视频，更新 " + updatedVideos + " 个视频元数据。");
            logs.add("同步只刷新元数据，真正花钱的步骤是右侧手动开始处理。");
            if (failedVideos > 0) {
                logs.add("失败 " + failedVideos + " 个视频，请查看 errors。");
            }
            catalogCacheService.markFolderVideosRefreshed(folderId);
            return new FolderVideoSyncResponse(folder.getTitle(), failedVideos, logs, errors, currentStats());
        } catch (BilibiliClientException exception) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, exception.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private boolean upsertFolder(long uid, BilibiliFolderMetadata remoteFolder) {
        Folder existing = folderMapper.selectById(remoteFolder.folderId());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            folderMapper.insert(Folder.builder()
                .folderId(remoteFolder.folderId())
                .uid(uid)
                .title(remoteFolder.title())
                .mediaCount(remoteFolder.mediaCount())
                .createdAt(now)
                .updatedAt(now)
                .build());
            return true;
        }

        existing.setUid(uid);
        existing.setTitle(remoteFolder.title());
        existing.setMediaCount(remoteFolder.mediaCount());
        existing.setUpdatedAt(now);
        folderMapper.updateById(existing);
        return false;
    }

    private boolean upsertVideo(Long folderId, BilibiliVideoMetadata remoteVideo) {
        Video existing = videoMapper.selectById(remoteVideo.bvid());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            videoMapper.insert(Video.builder()
                .bvid(remoteVideo.bvid())
                .folderId(folderId)
                .title(remoteVideo.title())
                .upName(remoteVideo.upName())
                .coverUrl(remoteVideo.coverUrl())
                .duration(remoteVideo.duration())
                .publishedAt(remoteVideo.publishedAt())
                .createdAt(now)
                .updatedAt(now)
                .isInvalid(remoteVideo.invalid() ? 1 : 0)
                .build());
            return true;
        }

        existing.setFolderId(folderId);
        existing.setTitle(remoteVideo.title());
        existing.setUpName(remoteVideo.upName());
        existing.setCoverUrl(remoteVideo.coverUrl());
        existing.setDuration(remoteVideo.duration());
        existing.setPublishedAt(remoteVideo.publishedAt());
        existing.setIsInvalid(remoteVideo.invalid() ? 1 : 0);
        existing.setUpdatedAt(now);
        videoMapper.updateById(existing);
        return false;
    }

    private CatalogStatsResponse currentStats() {
        return new CatalogStatsResponse(folderMapper.selectCount(null), videoMapper.selectCount(null));
    }

    private long resolveUid(Long requestedUid) {
        if (requestedUid != null && requestedUid > 0) {
            return requestedUid;
        }
        Long configuredUid = appProperties.getBilibili().getUid();
        if (configuredUid != null && configuredUid > 0) {
            return configuredUid;
        }
        AuthSessionVO session = authService.getSession();
        if (session.loggedIn() && session.uid() != null && session.uid() > 0) {
            return session.uid();
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "请先扫码登录 Bilibili，或提供 uid。", HttpStatus.BAD_REQUEST);
    }
}


