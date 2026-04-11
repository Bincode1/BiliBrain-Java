package com.bin.bilibrain.bilibili;

import java.util.Optional;

public interface BilibiliSubtitleClient {
    Optional<BilibiliSubtitlePayload> fetchSubtitle(String bvid, Long cid);
}
