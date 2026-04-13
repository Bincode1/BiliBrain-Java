package com.bin.bilibrain.chat;

import com.bin.bilibrain.model.entity.ChatConversation;
import com.bin.bilibrain.service.agent.AgentScopeService;
import com.bin.bilibrain.service.chat.ConversationMemoryService;
import com.bin.bilibrain.service.chat.ConversationService;
import com.bin.bilibrain.mapper.ChatConversationContextStatMapper;
import com.bin.bilibrain.mapper.ChatConversationMapper;
import com.bin.bilibrain.mapper.ChatConversationMemoryMapper;
import com.bin.bilibrain.mapper.ChatMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationScopeTest {

    @Test
    void ensureConversationUsesCurrentRequestFolderScopeForExistingConversation() {
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        ChatConversationMemoryMapper memoryMapper = mock(ChatConversationMemoryMapper.class);
        ChatConversationContextStatMapper contextStatMapper = mock(ChatConversationContextStatMapper.class);
        ConversationMemoryService conversationMemoryService = mock(ConversationMemoryService.class);
        AgentScopeService agentScopeService = mock(AgentScopeService.class);

        ChatConversation stored = ChatConversation.builder()
            .id("conv-1")
            .title("旧会话")
            .conversationType("GENERAL")
            .folderId(null)
            .videoBvid(null)
            .updatedAt(LocalDateTime.now().minusMinutes(1))
            .build();
        ChatConversation updated = ChatConversation.builder()
            .id("conv-1")
            .title("旧会话")
            .conversationType("GENERAL")
            .folderId(3003L)
            .videoBvid(null)
            .updatedAt(LocalDateTime.now())
            .build();

        when(conversationMapper.selectById("conv-1")).thenReturn(stored, updated);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(messageMapper.selectCount(any())).thenReturn(0L);
        when(agentScopeService.hasExplicitScope("folder", 3003L, null)).thenReturn(true);
        when(agentScopeService.resolveScope("folder", 3003L, null))
            .thenReturn(new AgentScopeService.ScopeSelection("folder", 3003L, null));
        doNothing().when(conversationMemoryService).compactIfNeeded(any());

        ConversationService conversationService = new ConversationService(
            conversationMapper,
            messageMapper,
            memoryMapper,
            contextStatMapper,
            conversationMemoryService,
            agentScopeService,
            new ObjectMapper()
        );

        var result = conversationService.ensureConversation("conv-1", "忽略", "GENERAL", 3003L, null, "folder");

        assertThat(result.id()).isEqualTo("conv-1");
        assertThat(result.folderId()).isEqualTo(3003L);
        assertThat(result.videoBvid()).isEmpty();
    }

    @Test
    void ensureConversationClearsStoredScopeWhenCurrentRequestExplicitlyUsesGlobalScope() {
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        ChatConversationMemoryMapper memoryMapper = mock(ChatConversationMemoryMapper.class);
        ChatConversationContextStatMapper contextStatMapper = mock(ChatConversationContextStatMapper.class);
        ConversationMemoryService conversationMemoryService = mock(ConversationMemoryService.class);
        AgentScopeService agentScopeService = mock(AgentScopeService.class);

        ChatConversation stored = ChatConversation.builder()
            .id("conv-2")
            .title("旧会话")
            .conversationType("GENERAL")
            .folderId(4004L)
            .videoBvid("BV1scope111")
            .updatedAt(LocalDateTime.now().minusMinutes(1))
            .build();
        ChatConversation updated = ChatConversation.builder()
            .id("conv-2")
            .title("旧会话")
            .conversationType("GENERAL")
            .folderId(null)
            .videoBvid(null)
            .updatedAt(LocalDateTime.now())
            .build();

        when(conversationMapper.selectById("conv-2")).thenReturn(stored, updated);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(messageMapper.selectCount(any())).thenReturn(0L);
        when(agentScopeService.hasExplicitScope("global", null, null)).thenReturn(true);
        when(agentScopeService.resolveScope("global", null, null))
            .thenReturn(new AgentScopeService.ScopeSelection("global", null, null));
        doNothing().when(conversationMemoryService).compactIfNeeded(any());

        ConversationService conversationService = new ConversationService(
            conversationMapper,
            messageMapper,
            memoryMapper,
            contextStatMapper,
            conversationMemoryService,
            agentScopeService,
            new ObjectMapper()
        );

        var result = conversationService.ensureConversation("conv-2", "忽略", "GENERAL", null, null, "global");

        assertThat(result.id()).isEqualTo("conv-2");
        assertThat(result.folderId()).isNull();
        assertThat(result.videoBvid()).isEmpty();
    }
}
