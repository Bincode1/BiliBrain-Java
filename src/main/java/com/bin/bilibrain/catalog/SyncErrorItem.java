package com.bin.bilibrain.catalog;

public record SyncErrorItem(
    String bvid,
    String title,
    String error
) {
}
