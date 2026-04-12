package com.bin.bilibrain.chat;

import com.bin.bilibrain.controller.ChatController;
import com.bin.bilibrain.exception.GlobalExceptionHandler;
import com.bin.bilibrain.model.entity.ChatMessage;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.service.chat.ConversationService;
import com.bin.bilibrain.service.chat.SseEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import({GlobalExceptionHandler.class, SseEventService.class})
class SseContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    void askStreamProducesStableSseFrameNames() throws Exception {
        when(conversationService.createConversation(any())).thenReturn(
            new ChatConversationVO("conv-sse", "SSE 会话", "GENERAL", "", 0, "", "2026-04-12T12:10:00")
        );
        when(conversationService.appendMessage(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(
                ChatMessage.builder()
                    .id(1L)
                    .conversationId("conv-sse")
                    .role("USER")
                    .content("你好")
                    .createdAt(LocalDateTime.now())
                    .build(),
                ChatMessage.builder()
                    .id(2L)
                    .conversationId("conv-sse")
                    .role("ASSISTANT")
                    .content("direct mode 回答")
                    .createdAt(LocalDateTime.now())
                    .build()
            );

        MvcResult mvcResult = mockMvc.perform(post("/api/ask/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"你好，帮我总结一下"}
                    """))
            .andExpect(request().asyncStarted())
            .andReturn();

        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andReturn();

        String content = asyncResult.getResponse().getContentAsString();

        assertThat(asyncResult.getResponse().getContentType()).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(content).contains("event:conversation");
        assertThat(content).contains("event:status");
        assertThat(content).contains("event:answer");
        assertThat(content).contains("event:answer_normalized");
        assertThat(content).contains("event:done");
        assertThat(content).contains("\"conversation_id\":\"conv-sse\"");
    }
}
