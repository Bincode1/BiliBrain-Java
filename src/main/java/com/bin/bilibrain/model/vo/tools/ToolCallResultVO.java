package com.bin.bilibrain.model.vo.tools;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ToolCallResultVO(
    Long callId,
    String toolName,
    String status,
    Map<String, Object> result,
    String createdAt
) {
}
