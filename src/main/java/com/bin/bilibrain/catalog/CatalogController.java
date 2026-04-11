package com.bin.bilibrain.catalog;

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
    public FolderListResponse listFolders(@RequestParam(required = false) Long uid) {
        return catalogService.listFolders(uid);
    }

    @GetMapping("/folders/{folderId}/videos")
    public FolderVideosResponse listFolderVideos(@PathVariable Long folderId) {
        return catalogService.listFolderVideos(folderId);
    }

    @PostMapping("/folders/sync")
    public FolderSyncResponse syncFolders(@RequestBody(required = false) FolderSyncRequest request) {
        Long requestedUid = request == null ? null : request.uid();
        return catalogSyncService.syncFolders(requestedUid);
    }

    @PostMapping("/sync")
    public FolderVideoSyncResponse syncFolderVideos(@Valid @RequestBody FolderVideoSyncRequest request) {
        return catalogSyncService.syncFolderMetadata(request.folderId());
    }
}
