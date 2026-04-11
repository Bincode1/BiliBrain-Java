package com.bin.bilibrain.ingestion;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.service.media.AudioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AudioStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadAudioCopiesFileIntoLocalStorageAndBuildsStableUrl() throws Exception {
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setAudioDir(tempDir.resolve("audio"));
        AudioStorageService audioStorageService = new AudioStorageService(appProperties);

        Path sourceFile = tempDir.resolve("source.m4a");
        Files.writeString(sourceFile, "demo-audio");

        AudioStorageService.AudioObjectRef ref = audioStorageService.uploadAudio(sourceFile, "BV1audio11111");

        assertThat(ref.provider()).isEqualTo(AudioStorageService.PROVIDER_NAME);
        assertThat(ref.objectKey()).isEqualTo("BV1audio11111.m4a");
        assertThat(ref.url()).isEqualTo("/storage/audio/BV1audio11111.m4a");
        assertThat(audioStorageService.exists(ref.provider(), ref.objectKey())).isTrue();
        assertThat(Files.readString(audioStorageService.resolvePath(ref.provider(), ref.objectKey()))).isEqualTo("demo-audio");
    }
}
