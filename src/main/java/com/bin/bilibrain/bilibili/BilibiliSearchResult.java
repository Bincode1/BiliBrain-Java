package com.bin.bilibrain.bilibili;

import java.util.List;

public record BilibiliSearchResult(
    String keyword,
    int page,
    int pageSize,
    int total,
    List<BilibiliSearchVideo> results
) {
}
