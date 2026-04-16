package com.bin.bilibrain.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StorageBootstrap implements ApplicationRunner {
    private final AppProperties appProperties;
    private final ProjectPathResolver projectPathResolver;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        createDirectories(List.of(
            appProperties.getStorage().getDataDir(),
            appProperties.getStorage().getUploadDir(),
            appProperties.getStorage().getAudioDir(),
            appProperties.getStorage().getVectorDbDir(),
            appProperties.getStorage().getToolsWorkspaceRoot(),
            projectPathResolver.resolveFromProjectRoot(appProperties.getStorage().getSkillsRoot())
        ));
    }

    private void createDirectories(List<Path> directories) throws IOException {
        for (Path directory : directories) {
            Files.createDirectories(directory.toAbsolutePath().normalize());
        }
    }
}
