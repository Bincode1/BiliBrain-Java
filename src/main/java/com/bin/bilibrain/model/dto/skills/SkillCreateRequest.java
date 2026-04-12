package com.bin.bilibrain.model.dto.skills;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SkillCreateRequest(
    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 过长")
    String name,
    @NotBlank(message = "description 不能为空")
    @Size(max = 300, message = "description 过长")
    String description,
    @NotBlank(message = "content 不能为空")
    String content
) {
}
