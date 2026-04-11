package com.bin.bilibrain.bilibili;

import java.util.List;

public interface BilibiliMetadataClient {
    List<BilibiliFolderMetadata> listFolders(long uid);

    List<BilibiliVideoMetadata> listFolderVideos(long folderId);
}
