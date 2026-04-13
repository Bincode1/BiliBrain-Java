package com.bin.bilibrain.model.dto.agent;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentStreamRequest(
    String conversationId,
    String conversationType,
    Long folderId,
    @JsonAlias("bvid")
    String videoBvid,
    String scopeMode,
    @JsonAlias("query")
    @NotBlank(message = "message 不能为空")
    String message
) {
}
