package com.bin.bilibrain.model.dto.chat;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateConversationRequest(
    String title,
    String conversationType,
    String videoBvid
) {
}
