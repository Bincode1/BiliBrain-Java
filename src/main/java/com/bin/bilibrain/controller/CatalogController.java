package com.bin.bilibrain.controller;

import com.bin.bilibrain.common.BaseResponse;
import com.bin.bilibrain.common.ResultUtils;
import com.bin.bilibrain.model.dto.catalog.FolderSyncRequest;
import com.bin.bilibrain.model.dto.catalog.FolderVideoSyncRequest;
import com.bin.bilibrain.model.vo.catalog.FolderListResponse;
import com.bin.bilibrain.model.vo.catalog.FolderBiliSearchResponse;
import com.bin.bilibrain.model.vo.catalog.FolderSyncResponse;
import com.bin.bilibrain.model.vo.catalog.FolderVideoSyncResponse;
import com.bin.bilibrain.model.vo.catalog.FolderVideosResponse;
import com.bin.bilibrain.service.catalog.CatalogService;
import com.bin.bilibrain.service.catalog.CatalogSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalogService;
    private final CatalogSyncService catalogSyncService;

    @GetMapping("/folders")
    public BaseResponse<FolderListResponse> listFolders(@RequestParam(required = false) Long uid) {
        return ResultUtils.success(catalogService.listFolders(uid));
    }

    @GetMapping("/folders/{folderId}/videos")
    public BaseResponse<FolderVideosResponse> listFolderVideos(@PathVariable Long folderId) {
        return ResultUtils.success(catalogService.listFolderVideos(folderId));
    }

    @GetMapping("/folders/{folderId}/bili-search")
    public BaseResponse<FolderBiliSearchResponse> searchBiliVideosForFolder(
        @PathVariable Long folderId,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "page_size", defaultValue = "12") int pageSize
    ) {
        return ResultUtils.success(catalogService.searchBiliVideosForFolder(folderId, keyword, page, pageSize));
    }

    @PostMapping("/folders/sync")
    public BaseResponse<FolderSyncResponse> syncFolders(@RequestBody(required = false) FolderSyncRequest request) {
        Long requestedUid = request == null ? null : request.uid();
        return ResultUtils.success(catalogSyncService.syncFolders(requestedUid));
    }

    @PostMapping("/sync")
    public BaseResponse<FolderVideoSyncResponse> syncFolderVideos(@Valid @RequestBody FolderVideoSyncRequest request) {
        return ResultUtils.success(catalogSyncService.syncFolderMetadata(request.folderId()));
    }
}

