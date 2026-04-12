package com.bin.bilibrain.model.vo.agent;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentApprovalItemVO(
    String toolId,
    String toolName,
    String arguments,
    String description
) {
}
