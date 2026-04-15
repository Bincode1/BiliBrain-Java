package com.bin.bilibrain.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AskRequest(
    String conversationId,
    String conversationType,
    Long folderId,
    @JsonAlias("bvid")
    String videoBvid,
    @JsonAlias("scope_mode")
    String scopeMode,
    @NotBlank(message = "query 不能为空")
    @JsonAlias("query")
    String message
) {
}
