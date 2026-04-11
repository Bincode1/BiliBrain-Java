package com.bin.bilibrain.service.media;

import com.bin.bilibrain.bilibili.BilibiliAudioTrack;
import com.bin.bilibrain.bilibili.BilibiliMetadataClient;
import com.bin.bilibrain.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class AudioDownloadService {
    private final BilibiliMetadataClient bilibiliMetadataClient;
    private final AppProperties appProperties;

    public DownloadedAudio download(String bvid) {
        if (!StringUtils.hasText(bvid)) {
            throw new IllegalArgumentException("bvid 不能为空。");
        }
        try {
            Path tempDir = appProperties.getStorage()
                .getUploadDir()
                .toAbsolutePath()
                .normalize()
                .resolve("ingestion-audio");
            Files.createDirectories(tempDir);

            Path outputPath = Files.createTempFile(tempDir, bvid.trim() + "-", ".m4a");
            BilibiliAudioTrack track = bilibiliMetadataClient.downloadAudioTrack(bvid.trim(), outputPath);
            return new DownloadedAudio(
                outputPath,
                track.cid(),
                track.mimeType(),
                track.bandwidth(),
                track.trackId()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("创建临时音频文件失败。", exception);
        }
    }

    public record DownloadedAudio(
        Path filePath,
        Long cid,
        String mimeType,
        int bandwidth,
        String trackId
    ) {
    }
}
