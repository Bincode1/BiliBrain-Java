package com.bin.bilibrain.integration;

import com.bin.bilibrain.model.entity.ChatConversation;
import com.bin.bilibrain.mapper.ChatConversationMapper;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ConversationScopePersistenceIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatConversationMapper chatConversationMapper;

    @MockitoBean
    private com.bin.bilibrain.service.agent.UnifiedAgentService unifiedAgentService;

    @Test
    void existingConversationCanBeSwitchedFromVideoScopeToGlobalScope() throws Exception {
        chatConversationMapper.insert(ChatConversation.builder()
            .id("conv-scope-reset")
            .title("旧会话")
            .conversationType("AGENT")
            .folderId(3908962660L)
            .videoBvid("BV1AZooYSE4B")
            .createdAt(LocalDateTime.now().minusMinutes(5))
            .updatedAt(LocalDateTime.now().minusMinutes(1))
            .build());

        when(unifiedAgentService.listScopeVideos(isNull(), eq("")))
            .thenReturn(new com.bin.bilibrain.service.agent.AgentExecutionResult(
                "当前范围已切换为全局。",
                "agent",
                "agent",
                "",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                null
            ));

        var mvcResult = mockMvc.perform(post("/api/skill-agent/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"当前你可以看到什么视频","folder_id":null,"video_bvid":null,"scope_mode":"global","conversation_id":"conv-scope-reset"}
                    """))
            .andExpect(request().asyncStarted())
            .andReturn();
        mvcResult.getAsyncResult(5000);

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk());

        ChatConversation conversation = chatConversationMapper.selectById("conv-scope-reset");
        assertThat(conversation.getFolderId()).isNull();
        assertThat(conversation.getVideoBvid()).isNull();
        verify(unifiedAgentService).listScopeVideos(isNull(), eq(""));
        verify(unifiedAgentService, never()).createStreamRuntime(anyString(), isNull(), eq(""));
    }
}
