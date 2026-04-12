package com.bin.bilibrain.model.vo.tools;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ToolDefinitionVO(
    String name,
    String description,
    boolean approvalRequired,
    boolean enabled
) {
}
