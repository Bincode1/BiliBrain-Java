package com.bin.bilibrain.integration;

import com.bin.bilibrain.model.dto.chat.CreateConversationRequest;
import com.bin.bilibrain.model.vo.agent.AgentApprovalItemVO;
import com.bin.bilibrain.model.vo.agent.AgentApprovalVO;
import com.bin.bilibrain.model.vo.agent.AgentSkillEventVO;
import com.bin.bilibrain.model.vo.agent.AgentToolEventVO;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import com.bin.bilibrain.service.agent.AgentExecutionResult;
import com.bin.bilibrain.service.agent.UnifiedAgentService;
import com.bin.bilibrain.service.chat.ConversationService;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ToolApprovalLifecycleTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConversationService conversationService;

    @MockitoBean
    private UnifiedAgentService unifiedAgentService;

    @Test
    void approvalStreamCanPauseThenResumeAndPersistAssistantReply() throws Exception {
        String conversationId = conversationService.createConversation(
            new CreateConversationRequest("Agent 审批", "AGENT", null, null)
        ).id();

        when(unifiedAgentService.execute(conversationId, null, "", "列出工作区")).thenReturn(
            new AgentExecutionResult(
                "",
                "agent",
                "agent",
                "命中需要审批的工具调用。",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new AgentApprovalVO(
                    conversationId,
                    List.of(new AgentApprovalItemVO(
                        "tool-1",
                        "list_workspaces",
                        "{}",
                        "读取工作区目录前需要人工确认。"
                    ))
                )
            )
        );
        when(unifiedAgentService.resume(eq(conversationId), eq(null), eq(""), any())).thenReturn(
            new AgentExecutionResult(
                "审批通过后，已经读取到 1 个工作区。",
                "agent",
                "agent",
                "审批通过后继续执行工具，并生成最终回答。",
                List.of(new ChatSourceVO("workspace", null, null, "默认工作区", "", null, null, "D:/workspace")),
                List.of(new SkillListItemVO("java-rag", "Java Agent 技能", "skills/java-rag/SKILL.md", true)),
                List.of(new AgentSkillEventVO("java-rag", "Java Agent 技能")),
                List.of(new AgentToolEventVO("list_workspaces", "返回 1 个工作区")),
                null
            )
        );

        MvcResult approvalResult = mockMvc.perform(post("/api/skill-agent/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"conversation_id":"%s","message":"列出工作区"}
                    """.formatted(conversationId)))
            .andExpect(request().asyncStarted())
            .andReturn();
        approvalResult.getAsyncResult(5000);

        String approvalContent = mockMvc.perform(asyncDispatch(approvalResult))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(approvalContent).contains("event:approval");
        assertThat(approvalContent).contains("\"tool_name\":\"list_workspaces\"");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ?",
            Integer.class,
            conversationId
        )).isEqualTo(2);

        MvcResult resumeResult = mockMvc.perform(post("/api/skill-agent/resume/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"conversation_id":"%s","feedbacks":[{"tool_id":"tool-1","decision":"APPROVED"}]}
                    """.formatted(conversationId)))
            .andExpect(request().asyncStarted())
            .andReturn();
        resumeResult.getAsyncResult(5000);

        String resumeContent = mockMvc.perform(asyncDispatch(resumeResult))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(resumeContent).contains("event:skills");
        assertThat(resumeContent).contains("event:tool");
        assertThat(resumeContent).contains("event:answer");
        assertThat(resumeContent).contains("event:answer_normalized");
        assertThat(resumeContent).contains("event:done");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ?",
            Integer.class,
            conversationId
        )).isEqualTo(3);
    }
}
