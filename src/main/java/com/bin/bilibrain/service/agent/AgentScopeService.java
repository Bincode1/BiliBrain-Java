package com.bin.bilibrain.service.agent;

import com.bin.bilibrain.mapper.FolderMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.model.entity.Folder;
import com.bin.bilibrain.model.entity.Video;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AgentScopeService {
    private final FolderMapper folderMapper;
    private final VideoMapper videoMapper;

    public ScopeSelection resolveScope(String scopeMode, Long folderId, String videoBvid) {
        String normalizedScopeMode = normalizeScopeMode(scopeMode);
        String normalizedVideoBvid = normalizeVideoBvid(videoBvid);
        if ("video".equals(normalizedScopeMode) && StringUtils.hasText(normalizedVideoBvid)) {
            return new ScopeSelection("video", folderId, normalizedVideoBvid);
        }
        if ("folder".equals(normalizedScopeMode) && folderId != null) {
            return new ScopeSelection("folder", folderId, null);
        }
        if ("global".equals(normalizedScopeMode)) {
            return new ScopeSelection("global", null, null);
        }
        if (StringUtils.hasText(normalizedVideoBvid)) {
            return new ScopeSelection("video", folderId, normalizedVideoBvid);
        }
        if (folderId != null) {
            return new ScopeSelection("folder", folderId, null);
        }
        return new ScopeSelection("global", null, null);
    }

    public boolean hasExplicitScope(String scopeMode, Long folderId, String videoBvid) {
        return StringUtils.hasText(scopeMode) || folderId != null || StringUtils.hasText(videoBvid);
    }

    public String describeScope(String scopeMode, Long folderId, String videoBvid) {
        ScopeSelection scope = resolveScope(scopeMode, folderId, videoBvid);
        if ("video".equals(scope.scopeMode()) && StringUtils.hasText(scope.videoBvid())) {
            Video video = videoMapper.selectById(scope.videoBvid());
            String title = video == null || !StringUtils.hasText(video.getTitle())
                ? scope.videoBvid()
                : video.getTitle().trim();
            if (scope.folderId() != null) {
                return "当前范围是单个视频：%s（bvid=%s，folder_id=%s）。"
                    .formatted(title, scope.videoBvid(), scope.folderId());
            }
            return "当前范围是单个视频：%s（bvid=%s）。".formatted(title, scope.videoBvid());
        }
        if ("folder".equals(scope.scopeMode()) && scope.folderId() != null) {
            Folder folder = folderMapper.selectById(scope.folderId());
            String title = folder == null || !StringUtils.hasText(folder.getTitle())
                ? "收藏夹 " + scope.folderId()
                : folder.getTitle().trim();
            return "当前范围是单个收藏夹：%s（folder_id=%s）。".formatted(title, scope.folderId());
        }
        return "当前范围是全部已入库内容。";
    }

    public String emptyContextMessage(String scopeMode, Long folderId, String videoBvid) {
        ScopeSelection scope = resolveScope(scopeMode, folderId, videoBvid);
        if ("video".equals(scope.scopeMode())) {
            return "当前视频还没有可检索内容。请先完成处理，或切换到收藏夹 / 全部范围。";
        }
        if ("folder".equals(scope.scopeMode())) {
            return "当前收藏夹还没有可检索内容。请先处理其中至少一个视频，或切换到全部范围。";
        }
        return "当前还没有可检索内容。请先完成至少一个视频的处理。";
    }

    public String normalizeScopeMode(String scopeMode) {
        if (!StringUtils.hasText(scopeMode)) {
            return "";
        }
        return scopeMode.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeVideoBvid(String videoBvid) {
        return StringUtils.hasText(videoBvid) ? videoBvid.trim() : null;
    }

    public record ScopeSelection(String scopeMode, Long folderId, String videoBvid) {
    }
}
