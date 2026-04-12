package com.bin.bilibrain.model.dto.agent;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentApprovalDecisionRequest(
    @NotBlank(message = "tool_id 不能为空")
    String toolId,
    @NotBlank(message = "decision 不能为空")
    String decision,
    String editedArguments
) {
}
