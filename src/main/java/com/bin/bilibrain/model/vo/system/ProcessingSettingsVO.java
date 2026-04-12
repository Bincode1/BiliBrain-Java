package com.bin.bilibrain.model.vo.system;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProcessingSettingsVO(
    int maxVideoMinutes
) {
}
