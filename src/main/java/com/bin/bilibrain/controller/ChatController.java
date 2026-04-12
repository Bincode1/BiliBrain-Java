package com.bin.bilibrain.controller;

import com.bin.bilibrain.common.BaseResponse;
import com.bin.bilibrain.common.ResultUtils;
import com.bin.bilibrain.model.dto.chat.AskStreamRequest;
import com.bin.bilibrain.model.dto.chat.CreateConversationRequest;
import com.bin.bilibrain.model.dto.chat.UpdateConversationRequest;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.model.vo.chat.ChatMessageVO;
import com.bin.bilibrain.service.chat.ConversationService;
import com.bin.bilibrain.service.chat.SseEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {
    private final ConversationService conversationService;
    private final SseEventService sseEventService;

    @GetMapping("/api/chat/conversations")
    public BaseResponse<List<ChatConversationVO>> listConversations() {
        return ResultUtils.success(conversationService.listConversations());
    }

    @PostMapping("/api/chat/conversations")
    public BaseResponse<ChatConversationVO> createConversation(@Valid @RequestBody CreateConversationRequest request) {
        return ResultUtils.success(conversationService.createConversation(request));
    }

    @PatchMapping("/api/chat/conversations/{conversationId}")
    public BaseResponse<ChatConversationVO> updateConversation(
        @PathVariable String conversationId,
        @Valid @RequestBody UpdateConversationRequest request
    ) {
        return ResultUtils.success(conversationService.updateConversation(conversationId, request));
    }

    @DeleteMapping("/api/chat/conversations/{conversationId}")
    public BaseResponse<Boolean> deleteConversation(@PathVariable String conversationId) {
        conversationService.deleteConversation(conversationId);
        return ResultUtils.success(true);
    }

    @GetMapping("/api/chat/history")
    public BaseResponse<List<ChatMessageVO>> getHistory(@RequestParam("conversation_id") String conversationId) {
        return ResultUtils.success(conversationService.getHistory(conversationId));
    }

    @PostMapping(path = "/api/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody AskStreamRequest request) {
        return sseEventService.streamDirectAnswer(request);
    }
}
