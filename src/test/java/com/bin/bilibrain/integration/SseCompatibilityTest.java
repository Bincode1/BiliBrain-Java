package com.bin.bilibrain.integration;

import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import com.bin.bilibrain.service.agent.AgentExecutionResult;
import com.bin.bilibrain.service.agent.UnifiedAgentService;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SseCompatibilityTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UnifiedAgentService unifiedAgentService;

    @Test
    void unifiedAgentStreamKeepsFrontendEventContractAndPersistsMessages() throws Exception {
        when(unifiedAgentService.execute(anyString(), isNull(), eq(""), anyString())).thenReturn(
            new AgentExecutionResult(
                "这是一个带 sources 的回答。",
                "agent",
                "agent",
                "命中知识库片段，按 Agent 模式组织回答。",
                List.of(new ChatSourceVO(
                    "chunk",
                    "BV1rag0001",
                    3003L,
                    "RAG 视频",
                    "BinCode",
                    12.0,
                    18.0,
                    "这里解释了知识库检索的关键步骤。"
                )),
                List.of(),
                List.of(new SkillListItemVO("java-rag", "Java RAG", "skills/java-rag/SKILL.md", true)),
                List.of(),
                List.of(),
                null
            )
        );

        MvcResult mvcResult = mockMvc.perform(post("/api/skill-agent/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"query":"帮我总结下这个收藏夹"}
                    """))
            .andExpect(request().asyncStarted())
            .andReturn();
        mvcResult.getAsyncResult(5000);

        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andReturn();

        String content = asyncResult.getResponse().getContentAsString();
        assertThat(content).contains("event:conversation");
        assertThat(content).contains("event:status");
        assertThat(content).contains("event:reasoning");
        assertThat(content).contains("event:route");
        assertThat(content).contains("event:mode");
        assertThat(content).contains("event:sources");
        assertThat(content).contains("event:answer");
        assertThat(content).contains("event:answer_normalized");
        assertThat(content).contains("event:skills");
        assertThat(content).contains("event:done");
        assertThat(content).doesNotContain("event:citation_segments");
        assertThat(content).contains("\"route\":\"agent\"");
        assertThat(content).contains("\"mode\":\"agent\"");

        Integer conversationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_conversations", Integer.class);
        Integer messageCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_messages", Integer.class);
        assertThat(conversationCount).isEqualTo(1);
        assertThat(messageCount).isEqualTo(2);
    }
}
