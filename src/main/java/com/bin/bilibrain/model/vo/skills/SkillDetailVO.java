package com.bin.bilibrain.model.vo.skills;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SkillDetailVO(
    String name,
    String description,
    String skillPath,
    String content,
    boolean active
) {
}
