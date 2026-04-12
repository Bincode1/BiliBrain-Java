package com.bin.bilibrain.model.dto.system;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProcessingSettingsUpdateRequest(
    @Min(1)
    @Max(300)
    int maxVideoMinutes
) {
}
