package com.bin.bilibrain.chat;

import com.bin.bilibrain.controller.ChatController;
import com.bin.bilibrain.exception.GlobalExceptionHandler;
import com.bin.bilibrain.model.vo.chat.AskResponse;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.model.vo.chat.ChatMessageVO;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
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
            new ChatConversationVO("conv-1", "Java AI", "GENERAL", null, "", 2, "最后一条消息", "2026-04-12T12:00:00")
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
            new ChatConversationVO("conv-2", "新对话", "VIDEO", 2002L, "BV1chat111", 0, "", "2026-04-12T12:01:00")
        );

        mockMvc.perform(post("/api/chat/conversations")
                .contentType("application/json")
                .content("""
                    {"title":"新对话","conversation_type":"video","folder_id":2002,"video_bvid":"BV1chat111"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("conv-2"))
            .andExpect(jsonPath("$.data.folder_id").value(2002))
            .andExpect(jsonPath("$.data.video_bvid").value("BV1chat111"));
    }

    @Test
    void updateConversationReturnsRenamedConversation() throws Exception {
        when(conversationService.updateConversation(eq("conv-3"), any())).thenReturn(
            new ChatConversationVO("conv-3", "重命名后的会话", "GENERAL", null, "", 3, "preview", "2026-04-12T12:02:00")
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
            new ChatMessageVO(1L, "conv-5", "USER", "你好", "[]", "", "", "2026-04-12T12:03:00"),
            new ChatMessageVO(2L, "conv-5", "ASSISTANT", "你好，这里是聊天壳。", "[]", "rag", "knowledge_base", "2026-04-12T12:03:05")
        ));

        mockMvc.perform(get("/api/chat/history").queryParam("conversation_id", "conv-5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].role").value("USER"))
            .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"))
            .andExpect(jsonPath("$.data[1].route_mode").value("knowledge_base"));
    }

    @Test
    void askReturnsSynchronousAnswerPayload() throws Exception {
        when(sseEventService.ask(any())).thenReturn(new AskResponse(
            "conv-ask-1",
            new ChatConversationVO("conv-ask-1", "同步问答", "GENERAL", null, "", 2, "preview", "2026-04-12T12:05:00"),
            "这是同步回答。",
            "video_summary",
            "rag",
            "命中摘要检索。",
            List.of(new ChatSourceVO("summary", "BV1ask00001", 2002L, "摘要视频", "BinCode", null, null, "摘要片段")),
            new ChatMessageVO(2L, "conv-ask-1", "ASSISTANT", "这是同步回答。", "[{\"source_type\":\"summary\"}]", "rag", "video_summary", "2026-04-12T12:05:05")
        ));

        mockMvc.perform(post("/api/ask")
                .contentType("application/json")
                .content("""
                    {"query":"给我一个同步回答"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversation_id").value("conv-ask-1"))
            .andExpect(jsonPath("$.data.answer").value("这是同步回答。"))
            .andExpect(jsonPath("$.data.route").value("video_summary"))
            .andExpect(jsonPath("$.data.mode").value("rag"))
            .andExpect(jsonPath("$.data.sources[0].source_type").value("summary"));
    }
}
