package com.bin.bilibrain.integration;

import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.service.chat.ChatAnswerResult;
import com.bin.bilibrain.service.chat.ChatAnswerService;
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
    private ChatAnswerService chatAnswerService;

    @Test
    void askStreamKeepsFrontendEventContractAndPersistsMessages() throws Exception {
        when(chatAnswerService.answer(anyString(), isNull(), eq(""), anyString())).thenReturn(
            new ChatAnswerResult(
                "这是一个带 sources 的回答。",
                "knowledge_base",
                "rag",
                "命中知识库片段，按 RAG 模式组织回答。",
                List.of(new ChatSourceVO(
                    "chunk",
                    "BV1rag0001",
                    3003L,
                    "RAG 视频",
                    "BinCode",
                    12.0,
                    18.0,
                    "这里解释了知识库检索的关键步骤。"
                ))
            )
        );

        MvcResult mvcResult = mockMvc.perform(post("/api/ask/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"query":"帮我总结下这个收藏夹"}
                    """))
            .andExpect(request().asyncStarted())
            .andReturn();

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
        assertThat(content).contains("event:done");
        assertThat(content).contains("\"route\":\"knowledge_base\"");
        assertThat(content).contains("\"mode\":\"rag\"");

        Integer conversationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_conversations", Integer.class);
        Integer messageCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_messages", Integer.class);
        assertThat(conversationCount).isEqualTo(1);
        assertThat(messageCount).isEqualTo(2);
    }

    @Test
    void askEndpointReturnsNormalizedPayloadAndPersistsMessages() throws Exception {
        when(chatAnswerService.answer(anyString(), isNull(), eq(""), anyString())).thenReturn(
            new ChatAnswerResult(
                "同步接口回答内容。",
                "video_summary",
                "rag",
                "同步问答命中摘要检索。",
                List.of(new ChatSourceVO(
                    "summary",
                    "BV1summary0001",
                    3003L,
                    "摘要视频",
                    "BinCode",
                    null,
                    null,
                    "这是一段摘要级上下文。"
                ))
            )
        );

        mockMvc.perform(post("/api/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"query":"给我一个同步回答"}
                    """))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(0))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.answer").value("同步接口回答内容。"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.route").value("video_summary"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.mode").value("rag"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.sources[0].source_type").value("summary"));

        Integer conversationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_conversations", Integer.class);
        Integer messageCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_messages", Integer.class);
        assertThat(conversationCount).isEqualTo(1);
        assertThat(messageCount).isEqualTo(2);
    }
}
