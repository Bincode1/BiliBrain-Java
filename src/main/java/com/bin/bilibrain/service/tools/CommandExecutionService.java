package com.bin.bilibrain.service.tools;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class CommandExecutionService {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_CHARS = 12_000;
    private static final Pattern DANGEROUS_COMMAND_PATTERN = Pattern.compile(
        "(?i)(?:^|\\s)(?:rm\\s+-rf|rd\\s+/s|del\\s+/s|format\\b|shutdown\\b|reboot\\b|mkfs\\b|diskpart\\b|poweroff\\b)"
    );

    private final WorkspaceService workspaceService;
    private final WorkspacePathResolver workspacePathResolver;
    private final AppProperties appProperties;

    public CommandExecutionService(
        WorkspaceService workspaceService,
        WorkspacePathResolver workspacePathResolver,
        AppProperties appProperties
    ) {
        this.workspaceService = workspaceService;
        this.workspacePathResolver = workspacePathResolver;
        this.appProperties = appProperties;
    }

    public Map<String, Object> runCommand(Long workspaceId, String command, String cwd, Integer timeoutSeconds) {
        String normalizedCommand = normalizeCommand(command);
        int effectiveTimeoutSeconds = normalizeTimeout(timeoutSeconds);
        Path workingDirectory = resolveWorkingDirectory(workspaceId, cwd);
        ensureSafeCommand(normalizedCommand);

        long startedAt = System.nanoTime();
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(buildShellCommand(normalizedCommand));
            builder.directory(workingDirectory.toFile());
            process = builder.start();
        } catch (IOException exception) {
            throw new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "启动命令执行失败。",
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                () -> readStream(process.getInputStream()),
                executor
            );
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                () -> readStream(process.getErrorStream()),
                executor
            );

            boolean completed = process.waitFor(effectiveTimeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
            }

            String stdout = awaitOutput(stdoutFuture, "读取标准输出失败。");
            String stderr = awaitOutput(stderrFuture, "读取标准错误失败。");
            long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("command", normalizedCommand);
            result.put("cwd", workingDirectory.toString());
            result.put("workspace_id", workspaceId);
            result.put("timeout_seconds", effectiveTimeoutSeconds);
            result.put("timed_out", !completed);
            result.put("exit_code", completed ? process.exitValue() : -1);
            result.put("success", completed && process.exitValue() == 0);
            result.put("stdout", truncate(stdout));
            result.put("stderr", truncate(stderr));
            result.put("stdout_truncated", stdout.length() > MAX_OUTPUT_CHARS);
            result.put("stderr_truncated", stderr.length() > MAX_OUTPUT_CHARS);
            result.put("elapsed_ms", elapsedMs);
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "命令执行被中断。",
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private Path resolveWorkingDirectory(Long workspaceId, String cwd) {
        Path root = workspaceId == null
            ? appProperties.getStorage().getToolsWorkspaceRoot().toAbsolutePath().normalize()
            : workspaceService.requireWorkspaceRoot(workspaceId);
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化命令工作目录失败。", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Path workingDirectory = workspacePathResolver.resolve(root, StringUtils.hasText(cwd) ? cwd : ".");
        if (!Files.exists(workingDirectory)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "运行目录不存在。", HttpStatus.NOT_FOUND);
        }
        if (!Files.isDirectory(workingDirectory)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "cwd 不是目录。", HttpStatus.BAD_REQUEST);
        }
        return workingDirectory;
    }

    private String normalizeCommand(String command) {
        if (!StringUtils.hasText(command)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "command 不能为空", HttpStatus.BAD_REQUEST);
        }
        return command.trim();
    }

    private void ensureSafeCommand(String command) {
        if (DANGEROUS_COMMAND_PATTERN.matcher(command).find()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前命令过于危险，已被策略拦截。", HttpStatus.BAD_REQUEST);
        }
    }

    private int normalizeTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        if (timeoutSeconds < 1 || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new BusinessException(
                ErrorCode.PARAMS_ERROR,
                "timeout_seconds 必须在 1 到 " + MAX_TIMEOUT_SECONDS + " 之间",
                HttpStatus.BAD_REQUEST
            );
        }
        return timeoutSeconds;
    }

    private List<String> buildShellCommand(String command) {
        if (isWindows()) {
            String utf8Command = "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; $OutputEncoding=[System.Text.Encoding]::UTF8; " + command;
            return List.of(
                "powershell.exe",
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                utf8Command
            );
        }
        return List.of("/bin/sh", "-lc", command);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private String readStream(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String awaitOutput(CompletableFuture<String> outputFuture, String message) {
        try {
            return outputFuture.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, message, HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (ExecutionException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, message, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_OUTPUT_CHARS) {
            return text == null ? "" : text;
        }
        return text.substring(0, MAX_OUTPUT_CHARS) + "\n...[truncated]";
    }
}
