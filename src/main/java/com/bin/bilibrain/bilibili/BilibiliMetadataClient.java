package com.bin.bilibrain.bilibili;

import java.nio.file.Path;
import java.util.List;

public interface BilibiliMetadataClient {
    List<BilibiliFolderMetadata> listFolders(long uid);

    List<BilibiliVideoMetadata> listFolderVideos(long folderId);

    BilibiliSearchResult searchVideos(String keyword, int page, int pageSize);

    BilibiliAudioTrack downloadAudioTrack(String bvid, Path outputPath);
}
