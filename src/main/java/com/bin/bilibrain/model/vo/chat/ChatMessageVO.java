package com.bin.bilibrain.model.vo.chat;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatMessageVO(
    Long id,
    String conversationId,
    String role,
    String content,
    String createdAt
) {
}
