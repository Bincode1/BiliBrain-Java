package com.bin.bilibrain.model.dto.tools;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkspaceCreateRequest(
    @NotBlank(message = "name 不能为空")
    @Size(max = 128, message = "name 过长")
    String name,
    @Size(max = 300, message = "description 过长")
    String description
) {
}
