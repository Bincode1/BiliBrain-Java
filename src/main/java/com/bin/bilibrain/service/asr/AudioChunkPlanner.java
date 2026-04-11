package com.bin.bilibrain.service.asr;

import com.bin.bilibrain.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AudioChunkPlanner {
    private static final Pattern SILENCE_START_PATTERN = Pattern.compile("silence_start:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern SILENCE_END_PATTERN = Pattern.compile("silence_end:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final String LEADING_PUNCTUATION = "^[ ，,。！？!?；;：:]+";

    private final AppProperties appProperties;

    public List<AudioChunkSpec> plan(Path audioPath) {
        double durationSeconds = probeDuration(audioPath);
        if (durationSeconds <= 0) {
            return List.of();
        }
        List<Double> silencePoints = detectSilencePoints(audioPath, durationSeconds);
        return buildPlan(durationSeconds, silencePoints);
    }

    public List<AudioChunkSpec> buildPlan(double durationSeconds, List<Double> silencePoints) {
        double totalDuration = Math.max(durationSeconds, 0.0);
        if (totalDuration <= 0) {
            return List.of();
        }

        double targetSeconds = Math.max(
            Math.min(appProperties.getProcessing().getAsrTargetChunkSeconds(), appProperties.getProcessing().getAsrChunkSeconds()),
            1.0
        );
        double maxSeconds = Math.max(appProperties.getProcessing().getAsrChunkSeconds(), targetSeconds);
        double minChunk = Math.min(Math.max(targetSeconds * 0.5, 20.0), targetSeconds);

        List<Double> cutPoints = silencePoints.stream()
            .map(point -> Math.round(point * 1000.0) / 1000.0)
            .filter(point -> point >= minChunk && point < totalDuration)
            .distinct()
            .sorted()
            .toList();

        double overlapSeconds = Math.max(appProperties.getProcessing().getAsrChunkOverlapSeconds(), 0);
        List<AudioChunkSpec> ranges = new ArrayList<>();
        double cursor = 0.0;
        int index = 0;
        while (cursor < totalDuration) {
            double hardEnd = Math.min(cursor + maxSeconds, totalDuration);
            double end;
            if (totalDuration - cursor <= maxSeconds) {
                end = totalDuration;
            } else {
                double chunkCursor = cursor;
                double preferredEnd = Math.min(cursor + targetSeconds, totalDuration);
                List<Double> candidates = cutPoints.stream()
                    .filter(point -> point >= chunkCursor + minChunk && point <= hardEnd)
                    .toList();
                if (candidates.isEmpty()) {
                    end = hardEnd;
                } else {
                    end = candidates.stream()
                        .min(Comparator.comparingDouble(point -> Math.abs(point - preferredEnd)))
                        .orElse(hardEnd);
                }
            }

            if (end <= cursor) {
                end = Math.min(cursor + maxSeconds, totalDuration);
            }
            double clipStart = index == 0 ? cursor : Math.max(0.0, cursor - overlapSeconds);
            ranges.add(new AudioChunkSpec(
                index,
                round3(cursor),
                round3(end),
                round3(clipStart),
                round3(end)
            ));
            cursor = end;
            index += 1;
        }
        return ranges;
    }

    public String trimRepeatedPrefix(String previousText, String currentText) {
        String previous = safeTrim(previousText);
        String current = safeTrim(currentText);
        if (previous.isBlank() || current.isBlank()) {
            return current;
        }

        int maxMatch = Math.min(Math.min(previous.length(), current.length()), 80);
        for (int size = maxMatch; size >= 8; size--) {
            if (previous.regionMatches(previous.length() - size, current, 0, size)) {
                return current.substring(size).replaceFirst(LEADING_PUNCTUATION, "").trim();
            }
        }
        return current;
    }

    public void extractChunk(Path audioPath, AudioChunkSpec chunkSpec, Path outputPath) {
        double duration = Math.max(chunkSpec.clipEndSeconds() - chunkSpec.clipStartSeconds(), 0.1);
        CommandResult result = runCommand(List.of(
            appProperties.getProcessing().getFfmpegCommand(),
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-ss",
            "%.3f".formatted(chunkSpec.clipStartSeconds()),
            "-i",
            audioPath.toAbsolutePath().normalize().toString(),
            "-t",
            "%.3f".formatted(duration),
            "-vn",
            "-ac",
            "1",
            "-ar",
            "16000",
            "-b:a",
            "48k",
            outputPath.toAbsolutePath().normalize().toString()
        ));
        if (result.exitCode() != 0) {
            throw new IllegalStateException("ffmpeg 切片失败: " + nonBlankOrDefault(result.stderr(), "unknown error"));
        }
    }

    protected double probeDuration(Path audioPath) {
        CommandResult result = runCommand(List.of(
            appProperties.getProcessing().getFfprobeCommand(),
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
            audioPath.toAbsolutePath().normalize().toString()
        ));
        if (result.exitCode() != 0) {
            throw new IllegalStateException("ffprobe 读取音频时长失败: " + nonBlankOrDefault(result.stderr(), "unknown error"));
        }
        try {
            return Double.parseDouble(result.stdout().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("ffprobe 返回了无效的音频时长。", exception);
        }
    }

    protected List<Double> detectSilencePoints(Path audioPath, double totalDuration) {
        CommandResult result = runCommand(List.of(
            appProperties.getProcessing().getFfmpegCommand(),
            "-hide_banner",
            "-i",
            audioPath.toAbsolutePath().normalize().toString(),
            "-af",
            "silencedetect=noise=%sdB:d=%s".formatted(
                appProperties.getProcessing().getAsrSilenceNoiseDb(),
                appProperties.getProcessing().getAsrSilenceMinSeconds()
            ),
            "-f",
            "null",
            "-"
        ));
        if (result.exitCode() != 0) {
            throw new IllegalStateException("ffmpeg 静音检测失败: " + nonBlankOrDefault(result.stderr(), "unknown error"));
        }

        List<Double> silenceStarts = new ArrayList<>();
        LinkedHashSet<Double> cutPoints = new LinkedHashSet<>();
        for (String line : result.stderr().lines().toList()) {
            Matcher startMatcher = SILENCE_START_PATTERN.matcher(line);
            if (startMatcher.find()) {
                silenceStarts.add(Double.parseDouble(startMatcher.group(1)));
                continue;
            }
            Matcher endMatcher = SILENCE_END_PATTERN.matcher(line);
            if (endMatcher.find() && !silenceStarts.isEmpty()) {
                double silenceEnd = Double.parseDouble(endMatcher.group(1));
                double silenceStart = silenceStarts.remove(0);
                double midpoint = (silenceStart + silenceEnd) / 2;
                if (midpoint > 0 && midpoint < totalDuration) {
                    cutPoints.add(midpoint);
                }
            }
        }
        return cutPoints.stream().toList();
    }

    private CommandResult runCommand(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).start();
            int exitCode = process.waitFor();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(exitCode, stdout, stderr);
        } catch (IOException exception) {
            throw new IllegalStateException("执行系统命令失败: " + command.getFirst(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("系统命令执行被中断。", exception);
        }
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String nonBlankOrDefault(String value, String fallback) {
        String normalized = safeTrim(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    public record AudioChunkSpec(
        int index,
        double startSeconds,
        double endSeconds,
        double clipStartSeconds,
        double clipEndSeconds
    ) {
    }

    private record CommandResult(
        int exitCode,
        String stdout,
        String stderr
    ) {
    }
}
