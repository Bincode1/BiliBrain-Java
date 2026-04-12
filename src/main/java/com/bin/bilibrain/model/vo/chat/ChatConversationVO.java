package com.bin.bilibrain.model.vo.chat;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatConversationVO(
    String id,
    String title,
    String conversationType,
    String videoBvid,
    int messageCount,
    String lastMessagePreview,
    String updatedAt
) {
}
