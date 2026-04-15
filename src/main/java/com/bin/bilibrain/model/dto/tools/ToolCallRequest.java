package com.bin.bilibrain.model.dto.tools;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ToolCallRequest(
    @NotBlank(message = "tool_name 不能为空")
    @Size(max = 64, message = "tool_name 过长")
    String toolName,
    Long workspaceId,
    @Size(max = 64, message = "skill_name 过长")
    String skillName,
    Map<String, Object> arguments
) {
}
