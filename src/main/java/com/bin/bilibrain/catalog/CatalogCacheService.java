package com.bin.bilibrain.catalog;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.entity.Folder;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.state.AppStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogCacheService {
    private static final String FOLDER_LIST_CACHE_PREFIX = "cache:folders";
    private static final String FOLDER_VIDEOS_CACHE_PREFIX = "cache:folder-videos";

    private final AppStateService appStateService;
    private final ObjectProvider<CatalogSyncService> catalogSyncServiceProvider;
    private final AppProperties appProperties;
    @Qualifier("applicationTaskExecutor")
    private final Executor executor;

    private final Map<String, CompletableFuture<Void>> refreshTasks = new ConcurrentHashMap<>();

    public String folderListCacheKey(long uid) {
        return FOLDER_LIST_CACHE_PREFIX + ":" + uid;
    }

    public String folderVideosCacheKey(long folderId) {
        return FOLDER_VIDEOS_CACHE_PREFIX + ":" + folderId;
    }

    public Optional<LocalDateTime> resolveFolderListCacheTime(long uid, List<Folder> folders) {
        return appStateService.getUpdatedAt(folderListCacheKey(uid))
            .or(() -> folders.stream()
                .map(Folder::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo));
    }

    public Optional<LocalDateTime> resolveFolderVideosCacheTime(long folderId, List<Video> videos) {
        return appStateService.getUpdatedAt(folderVideosCacheKey(folderId))
            .or(() -> videos.stream()
                .map(Video::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo));
    }

    public boolean isFolderListFresh(Optional<LocalDateTime> updatedAt) {
        return isFresh(updatedAt.orElse(null), appProperties.getCatalog().getFolderListCacheTtlSeconds());
    }

    public boolean isFolderVideosFresh(Optional<LocalDateTime> updatedAt) {
        return isFresh(updatedAt.orElse(null), appProperties.getCatalog().getFolderVideosCacheTtlSeconds());
    }

    public void markFolderListRefreshed(long uid) {
        appStateService.saveJson(folderListCacheKey(uid), Map.of("uid", uid));
    }

    public void markFolderVideosRefreshed(long folderId) {
        appStateService.saveJson(folderVideosCacheKey(folderId), Map.of("folder_id", folderId));
    }

    public void scheduleFolderListRefresh(long uid) {
        scheduleRefresh(folderListCacheKey(uid), () -> catalogSyncServiceProvider.getObject().syncFolders(uid));
    }

    public void scheduleFolderVideosRefresh(long folderId) {
        scheduleRefresh(folderVideosCacheKey(folderId), () -> catalogSyncServiceProvider.getObject().syncFolderMetadata(folderId));
    }

    private boolean isFresh(LocalDateTime updatedAt, int ttlSeconds) {
        if (updatedAt == null) {
            return false;
        }
        return Duration.between(updatedAt, LocalDateTime.now()).getSeconds() < Math.max(ttlSeconds, 1);
    }

    private void scheduleRefresh(String taskKey, Runnable operation) {
        refreshTasks.compute(taskKey, (key, existing) -> {
            if (existing != null && !existing.isDone()) {
                return existing;
            }
            CompletableFuture<Void> future = CompletableFuture.runAsync(operation, executor)
                .exceptionally(exception -> {
                    log.warn("catalog background refresh failed for {}", taskKey, exception);
                    return null;
                })
                .whenComplete((ignored, throwable) -> refreshTasks.remove(taskKey));
            return future;
        });
    }
}
