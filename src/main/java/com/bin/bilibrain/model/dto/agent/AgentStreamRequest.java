package com.bin.bilibrain.model.dto.agent;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentStreamRequest(
    String conversationId,
    Long folderId,
    String videoBvid,
    @NotBlank(message = "message 不能为空")
    String message
) {
}
