package com.bin.bilibrain.chat;

import com.bin.bilibrain.controller.ChatController;
import com.bin.bilibrain.exception.GlobalExceptionHandler;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.model.vo.chat.ChatMessageVO;
import com.bin.bilibrain.service.chat.ConversationService;
import com.bin.bilibrain.service.chat.SseEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import(GlobalExceptionHandler.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private SseEventService sseEventService;

    @Test
    void listConversationsReturnsConversationItems() throws Exception {
        when(conversationService.listConversations()).thenReturn(List.of(
            new ChatConversationVO("conv-1", "Java AI", "GENERAL", "", 2, "最后一条消息", "2026-04-12T12:00:00")
        ));

        mockMvc.perform(get("/api/chat/conversations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].id").value("conv-1"))
            .andExpect(jsonPath("$.data[0].message_count").value(2));
    }

    @Test
    void createConversationReturnsCreatedConversation() throws Exception {
        when(conversationService.createConversation(any())).thenReturn(
            new ChatConversationVO("conv-2", "新对话", "VIDEO", "BV1chat111", 0, "", "2026-04-12T12:01:00")
        );

        mockMvc.perform(post("/api/chat/conversations")
                .contentType("application/json")
                .content("""
                    {"title":"新对话","conversation_type":"video","video_bvid":"BV1chat111"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("conv-2"))
            .andExpect(jsonPath("$.data.video_bvid").value("BV1chat111"));
    }

    @Test
    void updateConversationReturnsRenamedConversation() throws Exception {
        when(conversationService.updateConversation(eq("conv-3"), any())).thenReturn(
            new ChatConversationVO("conv-3", "重命名后的会话", "GENERAL", "", 3, "preview", "2026-04-12T12:02:00")
        );

        mockMvc.perform(patch("/api/chat/conversations/conv-3")
                .contentType("application/json")
                .content("""
                    {"title":"重命名后的会话"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("重命名后的会话"));
    }

    @Test
    void deleteConversationReturnsSuccessFlag() throws Exception {
        doNothing().when(conversationService).deleteConversation("conv-4");

        mockMvc.perform(delete("/api/chat/conversations/conv-4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void getHistoryReturnsMessageList() throws Exception {
        when(conversationService.getHistory("conv-5")).thenReturn(List.of(
            new ChatMessageVO(1L, "conv-5", "USER", "你好", "2026-04-12T12:03:00"),
            new ChatMessageVO(2L, "conv-5", "ASSISTANT", "你好，这里是聊天壳。", "2026-04-12T12:03:05")
        ));

        mockMvc.perform(get("/api/chat/history").queryParam("conversation_id", "conv-5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].role").value("USER"))
            .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"));
    }
}
