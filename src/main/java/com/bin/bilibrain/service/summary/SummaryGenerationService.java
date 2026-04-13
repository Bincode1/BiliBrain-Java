package com.bin.bilibrain.service.summary;

import com.bin.bilibrain.ai.client.DashScopeChatClientFactory;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.model.entity.Video;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Service
public class SummaryGenerationService {
    private static final Logger log = LoggerFactory.getLogger(SummaryGenerationService.class);
    private static final String DIRECT_SYSTEM_PROMPT = """
        你是一个严谨的 B 站知识整理助手。
        请根据转写文本输出中文摘要，要求：
        1. 只基于给定内容，不要编造。
        2. 先概括主题，再提炼关键观点和可执行结论。
        3. 语言简洁清楚，避免废话和重复。
        """;

    private static final String WINDOW_SYSTEM_PROMPT = """
        你是一个转写片段摘要助手。
        请提炼这一段内容里的核心观点、关键术语、结论和上下文线索。
        输出中文摘要，避免照抄原文。
        """;

    private static final String REDUCE_SYSTEM_PROMPT = """
        你是一个多段摘要归并助手。
        请把多个窗口摘要整合为一个最终中文摘要，保留主线、关键观点和结论，
        去除重复信息，结构清晰，不要编造未出现的内容。
        """;

    private final DashScopeChatClientFactory chatClientFactory;
    private final AppProperties appProperties;
    private final Executor summaryExecutor;

    public SummaryGenerationService(
        DashScopeChatClientFactory chatClientFactory,
        AppProperties appProperties,
        @Qualifier("summaryTaskExecutor") ObjectProvider<Executor> executorProvider
    ) {
        this.chatClientFactory = chatClientFactory;
        this.appProperties = appProperties;
        this.summaryExecutor = executorProvider.getIfAvailable(() -> Runnable::run);
    }

    public boolean isAvailable() {
        return chatClientFactory.isAvailable();
    }

    public String generateDirectSummary(Video video, String transcriptText) {
        long startedAt = System.nanoTime();
        String summary = callModel(
            DIRECT_SYSTEM_PROMPT,
            """
                视频标题：%s
                UP 主：%s

                请基于以下完整转写生成最终摘要：

                %s
                """.formatted(safe(video.getTitle()), safe(video.getUpName()), safe(transcriptText))
        );
        log.info(
            "summary direct generated for bvid={} title={} transcriptChars={} costMs={}",
            safe(video.getBvid()),
            safe(video.getTitle()),
            safe(transcriptText).length(),
            elapsedMs(startedAt)
        );
        return summary;
    }

    public List<String> generateWindowSummaries(Video video, List<String> windows) {
        if (windows.isEmpty()) {
            return List.of();
        }
        int concurrency = Math.max(1, Math.min(appProperties.getSummary().getWindowConcurrency(), windows.size()));
        long startedAt = System.nanoTime();
        log.info(
            "summary window generation started for bvid={} title={} windows={} concurrency={}",
            safe(video.getBvid()),
            safe(video.getTitle()),
            windows.size(),
            concurrency
        );
        List<String> windowSummaries = new ArrayList<>(windows.size());
        for (int batchStart = 0; batchStart < windows.size(); batchStart += concurrency) {
            int batchEnd = Math.min(batchStart + concurrency, windows.size());
            List<CompletableFuture<IndexedSummary>> futures = new ArrayList<>(batchEnd - batchStart);
            for (int index = batchStart; index < batchEnd; index++) {
                int currentIndex = index;
                futures.add(CompletableFuture.supplyAsync(
                    () -> new IndexedSummary(
                        currentIndex,
                        generateSingleWindowSummary(video, windows, currentIndex)
                    ),
                    summaryExecutor
                ));
            }
            try {
                futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(java.util.Comparator.comparingInt(IndexedSummary::index))
                    .map(IndexedSummary::summary)
                    .forEach(windowSummaries::add);
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                log.warn(
                    "summary window generation failed for bvid={} title={} batchStart={} reason={}",
                    safe(video.getBvid()),
                    safe(video.getTitle()),
                    batchStart,
                    cause.getMessage(),
                    cause
                );
                throw toRuntimeException(cause);
            }
        }
        log.info(
            "summary window generation finished for bvid={} title={} windows={} costMs={}",
            safe(video.getBvid()),
            safe(video.getTitle()),
            windows.size(),
            elapsedMs(startedAt)
        );
        return windowSummaries;
    }

    public String reduceWindowSummaries(Video video, List<String> windowSummaries) {
        String mergedWindows = String.join("\n\n", windowSummaries);
        long startedAt = System.nanoTime();
        String summary = callModel(
            REDUCE_SYSTEM_PROMPT,
            """
                视频标题：%s
                请整合以下窗口摘要，输出一个最终摘要：

                %s
                """.formatted(safe(video.getTitle()), mergedWindows)
        );
        log.info(
            "summary reduce generated for bvid={} title={} windowSummaries={} mergedChars={} costMs={}",
            safe(video.getBvid()),
            safe(video.getTitle()),
            windowSummaries.size(),
            mergedWindows.length(),
            elapsedMs(startedAt)
        );
        return summary;
    }

    private String generateSingleWindowSummary(Video video, List<String> windows, int index) {
        return callModel(
            WINDOW_SYSTEM_PROMPT,
            """
                视频标题：%s
                当前窗口：%d/%d

                请概括以下转写片段：

                %s
                """.formatted(safe(video.getTitle()), index + 1, windows.size(), safe(windows.get(index)))
        );
    }

    private String callModel(String systemPrompt, String userPrompt) {
        ChatClient chatClient = chatClientFactory.createSummaryClient();
        if (chatClient == null) {
            throw new IllegalStateException("摘要模型未启用，请先配置 DashScope Chat。");
        }
        String content = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .content();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("摘要模型未返回有效内容。");
        }
        return content.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private RuntimeException toRuntimeException(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(throwable.getMessage(), throwable);
    }

    private record IndexedSummary(int index, String summary) {
    }
}
