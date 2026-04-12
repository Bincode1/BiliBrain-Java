package com.bin.bilibrain.service.summary;

import com.bin.bilibrain.model.entity.Video;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class SummaryGenerationService {
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

    private final ObjectProvider<ChatClient> summaryChatClientProvider;

    public SummaryGenerationService(@Qualifier("summaryChatClient") ObjectProvider<ChatClient> summaryChatClientProvider) {
        this.summaryChatClientProvider = summaryChatClientProvider;
    }

    public boolean isAvailable() {
        return summaryChatClientProvider.getIfAvailable() != null;
    }

    public String generateDirectSummary(Video video, String transcriptText) {
        return callModel(
            DIRECT_SYSTEM_PROMPT,
            """
                视频标题：%s
                UP 主：%s

                请基于以下完整转写生成最终摘要：

                %s
                """.formatted(safe(video.getTitle()), safe(video.getUpName()), safe(transcriptText))
        );
    }

    public List<String> generateWindowSummaries(Video video, List<String> windows) {
        List<String> windowSummaries = new ArrayList<>(windows.size());
        for (int index = 0; index < windows.size(); index++) {
            String summary = callModel(
                WINDOW_SYSTEM_PROMPT,
                """
                    视频标题：%s
                    当前窗口：%d/%d

                    请概括以下转写片段：

                    %s
                    """.formatted(safe(video.getTitle()), index + 1, windows.size(), safe(windows.get(index)))
            );
            windowSummaries.add(summary);
        }
        return windowSummaries;
    }

    public String reduceWindowSummaries(Video video, List<String> windowSummaries) {
        String mergedWindows = String.join("\n\n", windowSummaries);
        return callModel(
            REDUCE_SYSTEM_PROMPT,
            """
                视频标题：%s
                请整合以下窗口摘要，输出一个最终摘要：

                %s
                """.formatted(safe(video.getTitle()), mergedWindows)
        );
    }

    private String callModel(String systemPrompt, String userPrompt) {
        ChatClient chatClient = summaryChatClientProvider.getIfAvailable();
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
}
