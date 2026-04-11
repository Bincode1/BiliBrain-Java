package com.bin.bilibrain.service.media;

import com.bin.bilibrain.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@RequiredArgsConstructor
public class AudioStorageService {
    public static final String PROVIDER_NAME = "local";

    private final AppProperties appProperties;

    public AudioObjectRef uploadAudio(Path sourcePath, String bvid) {
        if (!StringUtils.hasText(bvid)) {
            throw new IllegalArgumentException("bvid 不能为空。");
        }
        try {
            Path normalizedSource = sourcePath.toAbsolutePath().normalize();
            if (!Files.exists(normalizedSource)) {
                throw new IllegalStateException("待缓存的音频文件不存在。");
            }

            Path audioDir = audioDirectory();
            Files.createDirectories(audioDir);
            String objectKey = bvid.trim() + ".m4a";
            Path destination = audioDir.resolve(objectKey).normalize();
            Files.copy(normalizedSource, destination, StandardCopyOption.REPLACE_EXISTING);
            return new AudioObjectRef(PROVIDER_NAME, objectKey, getAudioUrl(objectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("写入本地音频缓存失败。", exception);
        }
    }

    public boolean exists(String providerName, String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return false;
        }
        return Files.exists(resolvePath(providerName, objectKey));
    }

    public Path resolvePath(String providerName, String objectKey) {
        validateSupportedProvider(providerName);
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("音频对象 key 不能为空。");
        }
        return audioDirectory().resolve(objectKey.trim()).toAbsolutePath().normalize();
    }

    public String getAudioUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return "";
        }
        return "/storage/audio/" + UriUtils.encodePathSegment(objectKey.trim(), StandardCharsets.UTF_8);
    }

    public void delete(String providerName, String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            Files.deleteIfExists(resolvePath(providerName, objectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("删除本地音频缓存失败。", exception);
        }
    }

    private Path audioDirectory() {
        return appProperties.getStorage().getAudioDir().toAbsolutePath().normalize();
    }

    private void validateSupportedProvider(String providerName) {
        if (StringUtils.hasText(providerName) && !PROVIDER_NAME.equals(providerName.trim())) {
            throw new IllegalArgumentException("暂不支持的音频存储 provider: " + providerName);
        }
    }

    public record AudioObjectRef(
        String provider,
        String objectKey,
        String url
    ) {
    }
}
