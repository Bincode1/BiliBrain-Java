package com.bin.bilibrain.chat;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.mapper.ChatConversationContextStatMapper;
import com.bin.bilibrain.mapper.ChatConversationMemoryMapper;
import com.bin.bilibrain.mapper.ChatMessageMapper;
import com.bin.bilibrain.model.entity.ChatConversationContextStat;
import com.bin.bilibrain.model.entity.ChatConversationMemory;
import com.bin.bilibrain.model.entity.ChatMessage;
import com.bin.bilibrain.service.chat.ConversationMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMemoryServiceTest {

    private ChatMessageMapper chatMessageMapper;
    private ChatConversationMemoryMapper chatConversationMemoryMapper;
    private ChatConversationContextStatMapper chatConversationContextStatMapper;
    private ConversationMemoryService conversationMemoryService;

    @BeforeEach
    void setUp() {
        chatMessageMapper = mock(ChatMessageMapper.class);
        chatConversationMemoryMapper = mock(ChatConversationMemoryMapper.class);
        chatConversationContextStatMapper = mock(ChatConversationContextStatMapper.class);

        AppProperties appProperties = new AppProperties();
        appProperties.getChat().setCompactionTokenThreshold(10);
        appProperties.getChat().setRecentMessageLimit(2);
        appProperties.getChat().setMemoryMaxCharacters(1000);
        appProperties.getChat().setMemoryLineMaxCharacters(40);

        conversationMemoryService = new ConversationMemoryService(
            chatMessageMapper,
            chatConversationMemoryMapper,
            chatConversationContextStatMapper,
            appProperties
        );
    }

    @Test
    void compactIfNeededAppendsOlderMessagesIntoPersistentMemory() {
        String conversationId = "conv-memory";
        when(chatConversationContextStatMapper.selectById(conversationId)).thenReturn(
            ChatConversationContextStat.builder()
                .conversationId(conversationId)
                .promptTokens(8)
                .completionTokens(8)
                .build()
        );
        when(chatMessageMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
            message(1L, conversationId, "USER", "第一轮提问"),
            message(2L, conversationId, "ASSISTANT", "第一轮回答"),
            message(3L, conversationId, "USER", "第二轮提问"),
            message(4L, conversationId, "ASSISTANT", "第二轮回答")
        ));
        when(chatConversationMemoryMapper.selectById(conversationId)).thenReturn(
            ChatConversationMemory.builder()
                .conversationId(conversationId)
                .memoryText("")
                .sourceMessageCount(0)
                .updatedAt(LocalDateTime.now())
                .build()
        );

        conversationMemoryService.compactIfNeeded(conversationId);

        ArgumentCaptor<ChatConversationMemory> captor = ArgumentCaptor.forClass(ChatConversationMemory.class);
        verify(chatConversationMemoryMapper).updateById(captor.capture());
        ChatConversationMemory updated = captor.getValue();
        assertThat(updated.getSourceMessageCount()).isEqualTo(2);
        assertThat(updated.getMemoryText()).contains("- USER: 第一轮提问");
        assertThat(updated.getMemoryText()).contains("- ASSISTANT: 第一轮回答");
    }

    @Test
    void decoratePromptIncludesCompactedMemoryAndRecentTurns() {
        String conversationId = "conv-prompt";
        when(chatConversationMemoryMapper.selectById(conversationId)).thenReturn(
            ChatConversationMemory.builder()
                .conversationId(conversationId)
                .memoryText("用户一直在问 Java 与 AI 集成。")
                .sourceMessageCount(1)
                .updatedAt(LocalDateTime.now())
                .build()
        );
        when(chatMessageMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
            message(1L, conversationId, "USER", "历史提问"),
            message(2L, conversationId, "ASSISTANT", "历史回答"),
            message(3L, conversationId, "USER", "最近提问"),
            message(4L, conversationId, "ASSISTANT", "最近回答"),
            message(5L, conversationId, "USER", "当前问题")
        ));

        String decorated = conversationMemoryService.decoratePrompt(conversationId, "当前请求内容");

        assertThat(decorated).contains("已压缩历史记忆");
        assertThat(decorated).contains("用户一直在问 Java 与 AI 集成。");
        assertThat(decorated).contains("[USER] 最近提问");
        assertThat(decorated).contains("[ASSISTANT] 最近回答");
        assertThat(decorated).contains("当前请求：\n当前请求内容");
        assertThat(decorated).doesNotContain("[USER] 当前问题");
    }

    private ChatMessage message(Long id, String conversationId, String role, String content) {
        return ChatMessage.builder()
            .id(id)
            .conversationId(conversationId)
            .role(role)
            .content(content)
            .createdAt(LocalDateTime.now())
            .build();
    }
}
