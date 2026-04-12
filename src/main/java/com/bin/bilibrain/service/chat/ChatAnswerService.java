package com.bin.bilibrain.service.chat;

import com.bin.bilibrain.ai.client.DashScopeChatClientFactory;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.service.retrieval.KnowledgeBaseSearchService;
import com.bin.bilibrain.service.retrieval.VideoSummarySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatAnswerService {
    public static final String ROUTE_DIRECT = "direct";
    public static final String ROUTE_KNOWLEDGE_BASE = "knowledge_base";
    public static final String ROUTE_VIDEO_SUMMARY = "video_summary";
    public static final String MODE_DIRECT = "direct";
    public static final String MODE_RAG = "rag";

    private static final List<String> SUMMARY_KEYWORDS = List.of(
        "总结", "概括", "综述", "梳理", "主要讲了什么", "核心内容", "摘要", "回顾"
    );

    private final DashScopeChatClientFactory chatClientFactory;
    private final KnowledgeBaseSearchService knowledgeBaseSearchService;
    private final VideoSummarySearchService videoSummarySearchService;
    private final ConversationMemoryService conversationMemoryService;

    public boolean isAvailable() {
        return chatClientFactory.isAvailable();
    }

    public ChatAnswerResult answer(
        String conversationId,
        Long folderId,
        String videoBvid,
        String question
    ) {
        String normalizedQuestion = requireQuestion(question);
        ChatClient chatClient = requireChatClient();

        boolean summaryIntent = isSummaryIntent(normalizedQuestion);
        if (summaryIntent) {
            List<ChatSourceVO> summarySources = videoSummarySearchService.searchVideoSummaries(
                normalizedQuestion,
                folderId,
                videoBvid
            );
            if (!summarySources.isEmpty()) {
                return answerWithSources(
                    chatClient,
                    conversationId,
                    normalizedQuestion,
                    summarySources,
                    ROUTE_VIDEO_SUMMARY,
                    "命中视频摘要检索，优先使用 summary 级上下文回答概括类问题。"
                );
            }
        }

        List<ChatSourceVO> knowledgeSources = knowledgeBaseSearchService.searchKnowledgeBase(
            normalizedQuestion,
            folderId,
            videoBvid
        );
        if (!knowledgeSources.isEmpty()) {
            return answerWithSources(
                chatClient,
                conversationId,
                normalizedQuestion,
                knowledgeSources,
                ROUTE_KNOWLEDGE_BASE,
                "命中 transcript chunk 检索，优先使用细粒度片段回答事实与细节问题。"
            );
        }

        if (!summaryIntent) {
            List<ChatSourceVO> fallbackSummarySources = videoSummarySearchService.searchVideoSummaries(
                normalizedQuestion,
                folderId,
                videoBvid
            );
            if (!fallbackSummarySources.isEmpty()) {
                return answerWithSources(
                    chatClient,
                    conversationId,
                    normalizedQuestion,
                    fallbackSummarySources,
                    ROUTE_VIDEO_SUMMARY,
                    "chunk 检索未命中，回退到 summary 级上下文回答概括类问题。"
                );
            }
        }

        String answer = invokeModel(
            chatClient,
            conversationId,
            directSystemPrompt(),
            """
                用户问题：
                %s
                """.formatted(normalizedQuestion)
        );
        return new ChatAnswerResult(
            answer,
            ROUTE_DIRECT,
            MODE_DIRECT,
            "没有命中可用知识上下文，回退到 direct chat。",
            List.of()
        );
    }

    private ChatAnswerResult answerWithSources(
        ChatClient chatClient,
        String conversationId,
        String question,
        List<ChatSourceVO> sources,
        String route,
        String reasoning
    ) {
        String answer = invokeModel(
            chatClient,
            conversationId,
            ragSystemPrompt(route),
            buildRagUserPrompt(question, sources, route)
        );
        return new ChatAnswerResult(answer, route, MODE_RAG, reasoning, sources);
    }

    private String invokeModel(
        ChatClient chatClient,
        String conversationId,
        String systemPrompt,
        String userPrompt
    ) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
            .system(systemPrompt)
            .user(conversationMemoryService.decoratePrompt(conversationId, userPrompt));

        String answer = spec.call().content();
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "聊天模型没有返回有效内容。",
                HttpStatus.BAD_GATEWAY
            );
        }
        return answer.trim();
    }

    private String ragSystemPrompt(String route) {
        if (ROUTE_VIDEO_SUMMARY.equals(route)) {
            return """
                你是一个 B 站知识库问答助手。
                你现在拿到的是视频摘要级上下文，适合回答概括、总结、主线梳理类问题。
                只允许基于提供的上下文回答；如果上下文不足，请明确说明不知道，不要编造。
                输出中文，先直接回答，再给出简短结论。
                """;
        }
        return """
            你是一个 B 站知识库问答助手。
            你现在拿到的是 transcript chunk 级上下文，适合回答细节、事实和片段内容问题。
            只允许基于提供的上下文回答；如果上下文不足，请明确说明不知道，不要编造。
            输出中文，先直接回答，再简要补充依据。
            """;
    }

    private String directSystemPrompt() {
        return """
            你是一个中文助手。
            当前没有可用知识库上下文，请基于通用能力谨慎回答。
            如果问题明显依赖具体视频内容或知识库资料，请明确说明当前缺少上下文。
            """;
    }

    private String buildRagUserPrompt(String question, List<ChatSourceVO> sources, String route) {
        List<String> renderedSources = new ArrayList<>(sources.size());
        for (int index = 0; index < sources.size(); index++) {
            ChatSourceVO source = sources.get(index);
            String header = ROUTE_VIDEO_SUMMARY.equals(route)
                ? "[摘要 %d]".formatted(index + 1)
                : "[片段 %d]".formatted(index + 1);
            String timeWindow = source.startSeconds() == null && source.endSeconds() == null
                ? ""
                : "\n时间范围：" + safe(source.startSeconds()) + "s - " + safe(source.endSeconds()) + "s";
            renderedSources.add(
                """
                    %s
                    视频：%s (%s)
                    UP 主：%s%s
                    内容：
                    %s
                    """.formatted(
                    header,
                    safe(source.videoTitle()),
                    safe(source.bvid()),
                    safe(source.upName()),
                    timeWindow,
                    safe(source.excerpt())
                )
            );
        }
        return """
            用户问题：
            %s

            知识上下文：
            %s
            """.formatted(question, String.join("\n\n", renderedSources));
    }

    private boolean isSummaryIntent(String question) {
        return SUMMARY_KEYWORDS.stream().anyMatch(question::contains);
    }

    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "message 不能为空", HttpStatus.BAD_REQUEST);
        }
        return question.trim();
    }

    private ChatClient requireChatClient() {
        ChatClient chatClient = chatClientFactory.createQaClient();
        if (chatClient == null) {
            throw new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "聊天模型未启用，请先配置 DashScope Chat。",
                HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        return chatClient;
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
