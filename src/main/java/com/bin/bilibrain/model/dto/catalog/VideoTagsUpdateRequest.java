package com.bin.bilibrain.model.dto.catalog;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VideoTagsUpdateRequest(
    @NotNull(message = "tags 不能为空")
    List<String> tags
) {
}
