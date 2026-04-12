package com.bin.bilibrain.model.vo.system;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SystemReadinessVO(
    String status,
    List<ReadinessCheckVO> checks
) {
}
