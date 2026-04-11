package com.bin.bilibrain.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalogService;

    @GetMapping("/folders")
    public FolderListResponse listFolders(@RequestParam(required = false) Long uid) {
        return catalogService.listFolders(uid);
    }

    @GetMapping("/folders/{folderId}/videos")
    public FolderVideosResponse listFolderVideos(@PathVariable Long folderId) {
        return catalogService.listFolderVideos(folderId);
    }
}
