package com.bin.bilibrain.model.dto.skills;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SkillToggleRequest(
    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 过长")
    String name
) {
}
