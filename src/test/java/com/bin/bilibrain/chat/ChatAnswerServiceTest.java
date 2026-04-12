package com.bin.bilibrain.chat;

import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.service.chat.ChatAnswerResult;
import com.bin.bilibrain.service.chat.ChatAnswerService;
import com.bin.bilibrain.service.retrieval.KnowledgeBaseSearchService;
import com.bin.bilibrain.service.retrieval.VideoSummarySearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatAnswerServiceTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private ObjectProvider<ChatClient> chatClientProvider;
    private ObjectProvider<org.springframework.ai.chat.memory.ChatMemory> chatMemoryProvider;
    private KnowledgeBaseSearchService knowledgeBaseSearchService;
    private VideoSummarySearchService videoSummarySearchService;
    private ChatAnswerService chatAnswerService;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        chatClientProvider = mock(ObjectProvider.class);
        chatMemoryProvider = mock(ObjectProvider.class);
        knowledgeBaseSearchService = mock(KnowledgeBaseSearchService.class);
        videoSummarySearchService = mock(VideoSummarySearchService.class);

        when(chatClientProvider.getIfAvailable()).thenReturn(chatClient);
        when(chatMemoryProvider.getIfAvailable()).thenReturn(null);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        chatAnswerService = new ChatAnswerService(
            chatClientProvider,
            chatMemoryProvider,
            knowledgeBaseSearchService,
            videoSummarySearchService
        );
    }

    @Test
    void usesKnowledgeBaseRouteWhenChunkSourcesExist() {
        when(knowledgeBaseSearchService.searchKnowledgeBase("这个视频第几分钟讲了 RAG", null, "BV1qa111"))
            .thenReturn(List.of(
                new ChatSourceVO("chunk", "BV1qa111", 2002L, "RAG 细节讲解", "BinCode", 120.0, 180.0, "这里讲了检索增强生成的细节")
            ));
        when(callResponseSpec.content()).thenReturn("这是基于 chunk 的回答");

        ChatAnswerResult result = chatAnswerService.answer("conv-1", null, "BV1qa111", "这个视频第几分钟讲了 RAG");

        assertThat(result.route()).isEqualTo(ChatAnswerService.ROUTE_KNOWLEDGE_BASE);
        assertThat(result.mode()).isEqualTo(ChatAnswerService.MODE_RAG);
        assertThat(result.sources()).hasSize(1);
        assertThat(result.answer()).isEqualTo("这是基于 chunk 的回答");
    }

    @Test
    void usesVideoSummaryRouteWhenSummaryIntentAndSummarySourcesExist() {
        when(videoSummarySearchService.searchVideoSummaries("帮我总结一下这个视频", 3003L, null))
            .thenReturn(List.of(
                new ChatSourceVO("summary", "BV1sum111", 3003L, "视频摘要", "BinCode", null, null, "这是一个视频摘要")
            ));
        when(callResponseSpec.content()).thenReturn("这是基于 summary 的回答");

        ChatAnswerResult result = chatAnswerService.answer("conv-2", 3003L, null, "帮我总结一下这个视频");

        assertThat(result.route()).isEqualTo(ChatAnswerService.ROUTE_VIDEO_SUMMARY);
        assertThat(result.mode()).isEqualTo(ChatAnswerService.MODE_RAG);
        assertThat(result.sources()).hasSize(1);
        assertThat(result.answer()).isEqualTo("这是基于 summary 的回答");
    }

    @Test
    void fallsBackToDirectRouteWhenNoSourcesExist() {
        when(videoSummarySearchService.searchVideoSummaries("这个问题没有知识库上下文", null, null)).thenReturn(List.of());
        when(knowledgeBaseSearchService.searchKnowledgeBase("这个问题没有知识库上下文", null, null)).thenReturn(List.of());
        when(callResponseSpec.content()).thenReturn("这是 direct 模式回答");

        ChatAnswerResult result = chatAnswerService.answer("conv-3", null, null, "这个问题没有知识库上下文");

        assertThat(result.route()).isEqualTo(ChatAnswerService.ROUTE_DIRECT);
        assertThat(result.mode()).isEqualTo(ChatAnswerService.MODE_DIRECT);
        assertThat(result.sources()).isEmpty();
        assertThat(result.answer()).isEqualTo("这是 direct 模式回答");
    }
}
