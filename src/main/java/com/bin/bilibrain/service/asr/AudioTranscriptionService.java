package com.bin.bilibrain.service.asr;

import com.bin.bilibrain.ai.client.QwenAsrClient;
import com.bin.bilibrain.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AudioTranscriptionService {
    private final QwenAsrClient qwenAsrClient;
    private final AudioChunkPlanner audioChunkPlanner;
    private final AppProperties appProperties;
    private final Executor asrChunkExecutor;

    @Autowired
    public AudioTranscriptionService(
        QwenAsrClient qwenAsrClient,
        AudioChunkPlanner audioChunkPlanner,
        AppProperties appProperties,
        @Qualifier("asrChunkTaskExecutor") ObjectProvider<Executor> executorProvider
    ) {
        this(qwenAsrClient, audioChunkPlanner, appProperties, executorProvider.getIfAvailable(() -> Runnable::run));
    }

    public AudioTranscriptionService(
        QwenAsrClient qwenAsrClient,
        AudioChunkPlanner audioChunkPlanner,
        AppProperties appProperties
    ) {
        this(qwenAsrClient, audioChunkPlanner, appProperties, Runnable::run);
    }

    AudioTranscriptionService(
        QwenAsrClient qwenAsrClient,
        AudioChunkPlanner audioChunkPlanner,
        AppProperties appProperties,
        Executor asrChunkExecutor
    ) {
        this.qwenAsrClient = qwenAsrClient;
        this.audioChunkPlanner = audioChunkPlanner;
        this.appProperties = appProperties;
        this.asrChunkExecutor = asrChunkExecutor;
    }

    public AudioTranscriptionResult transcribe(Path audioPath, Consumer<AudioTranscriptionProgress> progressListener) {
        Path normalizedAudioPath = audioPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedAudioPath)) {
            throw new IllegalStateException("待转写的音频文件不存在。");
        }

        emitProgress(progressListener, new AudioTranscriptionProgress(
            "chunking",
            "正在分析静音并切分音频",
            0,
            0,
            null
        ));

        List<AudioChunkPlanner.AudioChunkSpec> chunkSpecs = audioChunkPlanner.plan(normalizedAudioPath);
        if (chunkSpecs.isEmpty()) {
            throw new IllegalStateException("音频切片为空，无法继续转写。");
        }

        Path tempDir = createTempDirectory();
        try {
            List<Path> chunkFiles = materializeChunks(normalizedAudioPath, tempDir, chunkSpecs);
            emitProgress(progressListener, new AudioTranscriptionProgress(
                "transcribing",
                "正在转写音频块 0/" + chunkSpecs.size(),
                chunkSpecs.size(),
                0,
                null
            ));

            List<String> rawTexts = transcribeChunks(chunkFiles, progressListener);
            List<AudioTranscriptSegment> segments = buildSegments(chunkSpecs, rawTexts);
            String transcriptText = segments.stream()
                .map(AudioTranscriptSegment::content)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));

            return new AudioTranscriptionResult(
                resolveSourceModel(),
                chunkSpecs.size(),
                segments.size(),
                transcriptText,
                segments,
                appProperties.getProcessing().getAsrTargetChunkSeconds(),
                appProperties.getProcessing().getAsrChunkSeconds(),
                appProperties.getProcessing().getAsrChunkOverlapSeconds()
            );
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public String resolveSourceModel() {
        return qwenAsrClient.modelLabel();
    }

    private List<Path> materializeChunks(Path audioPath, Path tempDir, List<AudioChunkPlanner.AudioChunkSpec> chunkSpecs) {
        List<Path> chunkFiles = new ArrayList<>(chunkSpecs.size());
        for (AudioChunkPlanner.AudioChunkSpec chunkSpec : chunkSpecs) {
            Path chunkPath = tempDir.resolve("chunk-%03d.mp3".formatted(chunkSpec.index())).toAbsolutePath().normalize();
            audioChunkPlanner.extractChunk(audioPath, chunkSpec, chunkPath);
            chunkFiles.add(chunkPath);
        }
        return chunkFiles;
    }

    private List<String> transcribeChunks(List<Path> chunkFiles, Consumer<AudioTranscriptionProgress> progressListener) {
        int chunkCount = chunkFiles.size();
        int concurrency = Math.max(appProperties.getProcessing().getAsrChunkConcurrency(), 1);
        List<String> results = new ArrayList<>(Collections.nCopies(chunkCount, ""));
        log.info("ASR chunk transcription started: chunks={}, concurrency={}", chunkCount, concurrency);

        try {
            ExecutorCompletionService<ChunkText> completionService = new ExecutorCompletionService<>(asrChunkExecutor);
            for (int index = 0; index < chunkFiles.size(); index++) {
                final int chunkIndex = index;
                completionService.submit(() -> new ChunkText(chunkIndex, transcribeChunk(chunkFiles.get(chunkIndex))));
            }

            for (int completed = 1; completed <= chunkCount; completed++) {
                ChunkText chunkText = completionService.take().get();
                results.set(chunkText.index(), chunkText.text());
                log.info(
                    "ASR chunk completed: {}/{} ({})",
                    completed,
                    chunkCount,
                    chunkFiles.get(chunkText.index()).getFileName()
                );
                emitProgress(progressListener, new AudioTranscriptionProgress(
                    "transcribing",
                    "正在转写音频块 " + completed + "/" + chunkCount,
                    chunkCount,
                    completed,
                    chunkText.index() + 1
                ));
            }
            return results;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("音频转写被中断。", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("音频转写失败。", cause);
        }
    }

    private String transcribeChunk(Path chunkPath) {
        log.info("ASR chunk started: {}", chunkPath.getFileName());
        return qwenAsrClient.transcribe(chunkPath);
    }

    private List<AudioTranscriptSegment> buildSegments(
        List<AudioChunkPlanner.AudioChunkSpec> chunkSpecs,
        List<String> rawTexts
    ) {
        List<AudioTranscriptSegment> segments = new ArrayList<>(chunkSpecs.size());
        String previousText = "";
        for (int index = 0; index < chunkSpecs.size(); index++) {
            AudioChunkPlanner.AudioChunkSpec chunkSpec = chunkSpecs.get(index);
            String text = normalizeText(rawTexts.get(index));
            if (chunkSpec.clipStartSeconds() < chunkSpec.startSeconds()) {
                text = audioChunkPlanner.trimRepeatedPrefix(previousText, text);
            }
            segments.add(new AudioTranscriptSegment(
                index,
                chunkSpec.startSeconds(),
                chunkSpec.endSeconds(),
                text
            ));
            if (StringUtils.hasText(text)) {
                previousText = text.trim();
            }
        }
        return segments;
    }

    private void emitProgress(Consumer<AudioTranscriptionProgress> progressListener, AudioTranscriptionProgress progress) {
        if (progressListener != null) {
            progressListener.accept(progress);
        }
    }

    private Path createTempDirectory() {
        try {
            return Files.createTempDirectory("bilibrain-asr-").toAbsolutePath().normalize();
        } catch (IOException exception) {
            throw new IllegalStateException("创建 ASR 临时目录失败。", exception);
        }
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new IllegalStateException("清理 ASR 临时目录失败。", exception);
                    }
                });
        } catch (IOException exception) {
            throw new IllegalStateException("清理 ASR 临时目录失败。", exception);
        }
    }

    private String normalizeText(String rawText) {
        return rawText == null ? "" : rawText.trim();
    }

    private record ChunkText(
        int index,
        String text
    ) {
    }

    public record AudioTranscriptionProgress(
        String stage,
        String message,
        int totalChunks,
        int completedChunks,
        Integer currentChunk
    ) {
    }

    public record AudioTranscriptSegment(
        int index,
        double startSeconds,
        double endSeconds,
        String content
    ) {
    }

    public record AudioTranscriptionResult(
        String model,
        int chunkCount,
        int segmentCount,
        String text,
        List<AudioTranscriptSegment> segments,
        int chunkTargetSeconds,
        int chunkMaxSeconds,
        int chunkOverlapSeconds
    ) {
    }
}
