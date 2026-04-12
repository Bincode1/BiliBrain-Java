package com.bin.bilibrain.model.dto.agent;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentResumeStreamRequest(
    @NotBlank(message = "conversation_id 不能为空")
    String conversationId,
    @NotEmpty(message = "feedbacks 不能为空")
    List<@Valid AgentApprovalDecisionRequest> feedbacks
) {
}
