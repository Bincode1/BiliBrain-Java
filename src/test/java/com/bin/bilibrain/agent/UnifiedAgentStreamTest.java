package com.bin.bilibrain.agent;

import com.bin.bilibrain.controller.SkillAgentController;
import com.bin.bilibrain.exception.GlobalExceptionHandler;
import com.bin.bilibrain.model.entity.ChatMessage;
import com.bin.bilibrain.model.vo.agent.AgentApprovalItemVO;
import com.bin.bilibrain.model.vo.agent.AgentApprovalVO;
import com.bin.bilibrain.model.vo.agent.AgentSkillEventVO;
import com.bin.bilibrain.model.vo.agent.AgentToolEventVO;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import com.bin.bilibrain.service.agent.AgentExecutionResult;
import com.bin.bilibrain.service.agent.AgentResumeService;
import com.bin.bilibrain.service.agent.SkillAgentSseService;
import com.bin.bilibrain.service.agent.UnifiedAgentService;
import com.bin.bilibrain.service.chat.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SkillAgentController.class)
@Import({GlobalExceptionHandler.class, SkillAgentSseService.class})
class UnifiedAgentStreamTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private UnifiedAgentService unifiedAgentService;

    @MockitoBean
    private AgentResumeService agentResumeService;

    @Test
    void streamProducesSkillsToolAndDoneEvents() throws Exception {
        when(conversationService.ensureConversation(any(), any(), any(), any(), any(), any())).thenReturn(
            new ChatConversationVO("conv-agent", "Agent 对话", "AGENT", 3003L, "BV1agent111", 0, "", "2026-04-12T13:20:00")
        );
        when(unifiedAgentService.execute("conv-agent", 3003L, "BV1agent111", "帮我总结一下这个视频")).thenReturn(
            new AgentExecutionResult(
                "这是统一 Agent 的回答。",
                "agent",
                "agent",
                "统一 Agent 已完成本轮工具编排与回答生成。",
                List.of(new ChatSourceVO("summary", "BV1agent111", 3003L, "Agent 视频", "BinCode", null, null, "这是一段摘要")),
                List.of(new SkillListItemVO("java-rag", "负责 Java RAG 问答", "skills/java-rag/SKILL.md", true)),
                List.of(new AgentSkillEventVO("java-rag", "负责 Java RAG 问答")),
                List.of(new AgentToolEventVO("search_video_summaries", "命中 1 条摘要结果")),
                null
            )
        );
        when(conversationService.appendMessage(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(ChatMessage.builder()
                .id(1L)
                .conversationId("conv-agent")
                .role("USER")
                .content("帮我总结一下这个视频")
                .sourcesJson("[]")
                .createdAt(LocalDateTime.now())
                .build());
        when(conversationService.appendAssistantMessage(
            anyString(),
            anyString(),
            anyList(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyList(),
            anyList(),
            anyList(),
            any()
        ))
            .thenReturn(ChatMessage.builder()
                .id(2L)
                .conversationId("conv-agent")
                .role("ASSISTANT")
                .content("这是统一 Agent 的回答。")
                .sourcesJson("[{\"source_type\":\"summary\"}]")
                .answerMode("agent")
                .routeMode("agent")
                .createdAt(LocalDateTime.now())
                .build());

        MvcResult mvcResult = mockMvc.perform(post("/api/skill-agent/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"帮我总结一下这个视频","folder_id":3003,"video_bvid":"BV1agent111"}
                    """))
            .andExpect(request().asyncStarted())
            .andReturn();
        mvcResult.getAsyncResult(5000);

        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andReturn();

        String content = asyncResult.getResponse().getContentAsString();
        assertThat(content).contains("event:conversation");
        assertThat(content).contains("event:skills");
        assertThat(content).contains("event:skill");
        assertThat(content).contains("event:tool");
        assertThat(content).contains("event:answer");
        assertThat(content).contains("event:answer_normalized");
        assertThat(content).contains("event:done");
        assertThat(content).contains("\"route\":\"agent\"");
    }

    @Test
    void streamProducesApprovalEventWhenAgentNeedsConfirmation() throws Exception {
        when(conversationService.ensureConversation(any(), any(), any(), any(), any(), any())).thenReturn(
            new ChatConversationVO("conv-approval", "Agent 审批", "AGENT", null, "", 0, "", "2026-04-12T13:22:00")
        );
        when(unifiedAgentService.execute("conv-approval", null, "", "列出工作区")).thenReturn(
            new AgentExecutionResult(
                "",
                "agent",
                "agent",
                "命中需要人工确认的工具调用，等待审批后继续执行。",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new AgentApprovalVO(
                    "conv-approval",
                    List.of(new AgentApprovalItemVO("tool-1", "list_workspaces", "{}", "访问工作区目录前需要人工确认。"))
                )
            )
        );
        when(conversationService.appendMessage(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(ChatMessage.builder()
                .id(3L)
                .conversationId("conv-approval")
                .role("USER")
                .content("列出工作区")
                .sourcesJson("[]")
                .createdAt(LocalDateTime.now())
                .build());
        when(conversationService.appendAssistantMessage(
            anyString(),
            anyString(),
            anyList(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyList(),
            anyList(),
            anyList(),
            any()
        ))
            .thenReturn(ChatMessage.builder()
                .id(4L)
                .conversationId("conv-approval")
                .role("ASSISTANT")
                .content("工具调用等待人工确认")
                .sourcesJson("[]")
                .answerMode("agent")
                .routeMode("agent")
                .agentStatus("工具调用等待人工确认")
                .createdAt(LocalDateTime.now())
                .build());

        MvcResult mvcResult = mockMvc.perform(post("/api/skill-agent/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"列出工作区"}
                    """))
            .andExpect(request().asyncStarted())
            .andReturn();
        mvcResult.getAsyncResult(5000);

        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andReturn();

        String content = asyncResult.getResponse().getContentAsString();
        assertThat(content).contains("event:approval");
        assertThat(content).contains("event:status");
        assertThat(content).contains("event:answer_normalized");
        assertThat(content).contains("\"tool_name\":\"list_workspaces\"");
    }

    @Test
    void streamUsesCurrentRequestFolderScopeForExistingConversation() throws Exception {
        when(conversationService.ensureConversation(any(), any(), any(), any(), any(), any())).thenReturn(
            new ChatConversationVO("conv-scope", "Agent 对话", "AGENT", 3003L, "", 2, "", "2026-04-13T12:00:00")
        );
        when(unifiedAgentService.execute("conv-scope", 3003L, "", "总结一下收藏夹")).thenReturn(
            new AgentExecutionResult(
                "这是当前收藏夹的总结。",
                "agent",
                "agent",
                "当前请求已明确指定收藏夹范围，优先使用该范围执行检索与总结。",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
            )
        );
        when(conversationService.appendMessage(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(ChatMessage.builder()
                .id(5L)
                .conversationId("conv-scope")
                .role("USER")
                .content("总结一下收藏夹")
                .sourcesJson("[]")
                .createdAt(LocalDateTime.now())
                .build());
        when(conversationService.appendAssistantMessage(
            anyString(),
            anyString(),
            anyList(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyList(),
            anyList(),
            anyList(),
            any()
        ))
            .thenReturn(ChatMessage.builder()
                .id(6L)
                .conversationId("conv-scope")
                .role("ASSISTANT")
                .content("这是当前收藏夹的总结。")
                .sourcesJson("[]")
                .answerMode("agent")
                .routeMode("agent")
                .createdAt(LocalDateTime.now())
                .build());

        MvcResult mvcResult = mockMvc.perform(post("/api/skill-agent/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"conversation_id":"conv-scope","message":"总结一下收藏夹","folder_id":3003,"scope_mode":"folder"}
                    """))
            .andExpect(request().asyncStarted())
            .andReturn();
        mvcResult.getAsyncResult(5000);

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk());

        verify(unifiedAgentService).execute("conv-scope", 3003L, "", "总结一下收藏夹");
    }
}
