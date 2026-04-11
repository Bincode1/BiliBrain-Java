package com.bin.bilibrain.ingestion;

import com.bin.bilibrain.bilibili.BilibiliAudioTrack;
import com.bin.bilibrain.bilibili.BilibiliMetadataClient;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.service.media.AudioDownloadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class AudioDownloadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void downloadDelegatesToBilibiliClientAndReturnsLocalTempFile() throws Exception {
        BilibiliMetadataClient bilibiliMetadataClient = mock(BilibiliMetadataClient.class);
        doAnswer(invocation -> {
            Path outputPath = invocation.getArgument(1);
            Files.writeString(outputPath, "downloaded-audio");
            return new BilibiliAudioTrack(24680L, "https://audio.example.com/demo.m4a", "audio/mp4", 32000, "30216");
        }).when(bilibiliMetadataClient).downloadAudioTrack(eq("BV1audio22222"), any(Path.class));

        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setUploadDir(tempDir.resolve("uploads"));
        AudioDownloadService audioDownloadService = new AudioDownloadService(bilibiliMetadataClient, appProperties);

        AudioDownloadService.DownloadedAudio downloadedAudio = audioDownloadService.download("BV1audio22222");

        assertThat(downloadedAudio.cid()).isEqualTo(24680L);
        assertThat(downloadedAudio.mimeType()).isEqualTo("audio/mp4");
        assertThat(downloadedAudio.trackId()).isEqualTo("30216");
        assertThat(downloadedAudio.filePath()).exists();
        assertThat(Files.readString(downloadedAudio.filePath())).isEqualTo("downloaded-audio");
    }
}
