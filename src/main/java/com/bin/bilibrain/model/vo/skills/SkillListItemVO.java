package com.bin.bilibrain.model.vo.skills;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SkillListItemVO(
    String name,
    String description,
    String skillPath,
    boolean active
) {
}
