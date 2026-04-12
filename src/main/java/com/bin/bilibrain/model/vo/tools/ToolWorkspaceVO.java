package com.bin.bilibrain.model.vo.tools;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ToolWorkspaceVO(
    Long id,
    String name,
    String workspaceKey,
    String workspacePath,
    String description,
    String updatedAt
) {
}
