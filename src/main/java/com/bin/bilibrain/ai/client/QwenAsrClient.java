package com.bin.bilibrain.ai.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.bin.bilibrain.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QwenAsrClient {
    private static final long MAX_AUDIO_BYTES = 10L * 1024L * 1024L;

    private final WebClient.Builder webClientBuilder;
    private final AppProperties appProperties;
    private final Environment environment;

    public String transcribe(Path audioPath) {
        Path normalizedAudioPath = audioPath.toAbsolutePath().normalize();
        byte[] audioBytes = readAudioBytes(normalizedAudioPath);
        long startedAt = System.nanoTime();
        log.info(
            "Qwen ASR started for {} ({} bytes, model={})",
            normalizedAudioPath.getFileName(),
            audioBytes.length,
            appProperties.getProcessing().getAsrApiModel()
        );
        CompletionResponse response = webClientBuilder.clone()
            .baseUrl(appProperties.getProcessing().getAsrApiBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + requireApiKey())
            .build()
            .post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(buildRequest(audioBytes, normalizedAudioPath))
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                clientResponse -> clientResponse.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(body -> new IllegalStateException(
                        "Qwen ASR 请求失败: status=%s, body=%s".formatted(clientResponse.statusCode().value(), body)
                    ))
            )
            .bodyToMono(CompletionResponse.class)
            .timeout(Duration.ofSeconds(appProperties.getProcessing().getAsrApiTimeoutSeconds()))
            .block();
        String transcript = extractTranscript(response);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        log.info("Qwen ASR completed for {} in {}ms", normalizedAudioPath.getFileName(), elapsedMs);
        return transcript;
    }

    public String modelLabel() {
        return "dashscope/" + appProperties.getProcessing().getAsrApiModel();
    }

    CompletionRequest buildRequest(byte[] audioBytes, Path audioPath) {
        AsrOptions asrOptions = new AsrOptions(
            resolveLanguage(),
            appProperties.getProcessing().isAsrEnableItn()
        );
        String dataUri = "data:%s;base64,%s".formatted(
            detectMimeType(audioPath),
            Base64.getEncoder().encodeToString(audioBytes)
        );
        return new CompletionRequest(
            appProperties.getProcessing().getAsrApiModel(),
            List.of(new ChatMessage(
                "user",
                List.of(new InputAudioContent("input_audio", new InputAudio(dataUri)))
            )),
            false,
            asrOptions
        );
    }

    private String extractTranscript(CompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("Qwen ASR 没有返回可用结果。");
        }
        CompletionChoice choice = response.choices().getFirst();
        if (choice.message() == null || !StringUtils.hasText(choice.message().content())) {
            throw new IllegalStateException("Qwen ASR 返回内容为空。");
        }
        return choice.message().content().trim();
    }

    private byte[] readAudioBytes(Path audioPath) {
        try {
            byte[] audioBytes = Files.readAllBytes(audioPath);
            if (audioBytes.length == 0) {
                throw new IllegalStateException("待转写音频为空。");
            }
            if (audioBytes.length > MAX_AUDIO_BYTES) {
                throw new IllegalStateException(
                    "音频分片超过 qwen3-asr-flash 的 10MB 限制，请缩短切片或降低码率。"
                );
            }
            return audioBytes;
        } catch (IOException exception) {
            throw new IllegalStateException("读取音频分片失败。", exception);
        }
    }

    private String requireApiKey() {
        String apiKey = environment.getProperty("spring.ai.dashscope.api-key", "");
        if (!StringUtils.hasText(apiKey)) {
            apiKey = System.getenv("DASHSCOPE_API_KEY");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("DashScope API Key 未配置。");
        }
        return apiKey.trim();
    }

    private String resolveLanguage() {
        List<String> languageHints = appProperties.getProcessing().getAsrLanguageHints();
        if (languageHints == null || languageHints.isEmpty()) {
            return "zh";
        }
        String language = languageHints.getFirst();
        return StringUtils.hasText(language) ? language.trim() : "zh";
    }

    private String detectMimeType(Path audioPath) {
        String fileName = audioPath.getFileName() == null ? "" : audioPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".wav")) {
            return "audio/wav";
        }
        if (fileName.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (fileName.endsWith(".flac")) {
            return "audio/flac";
        }
        if (fileName.endsWith(".ogg")) {
            return "audio/ogg";
        }
        return "audio/mpeg";
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CompletionRequest(
        String model,
        List<ChatMessage> messages,
        boolean stream,
        AsrOptions asrOptions
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ChatMessage(
        String role,
        List<InputAudioContent> content
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record InputAudioContent(
        String type,
        InputAudio inputAudio
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record InputAudio(
        String data
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record AsrOptions(
        String language,
        boolean enableItn
    ) {
    }

    record CompletionResponse(
        List<CompletionChoice> choices
    ) {
    }

    record CompletionChoice(
        CompletionMessage message
    ) {
    }

    record CompletionMessage(
        String content
    ) {
    }
}
