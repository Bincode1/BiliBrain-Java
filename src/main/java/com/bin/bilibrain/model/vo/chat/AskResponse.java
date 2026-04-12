package com.bin.bilibrain.model.vo.chat;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AskResponse(
    String conversationId,
    ChatConversationVO conversation,
    String answer,
    String route,
    String mode,
    String reasoning,
    List<ChatSourceVO> sources,
    ChatMessageVO message
) {
}
