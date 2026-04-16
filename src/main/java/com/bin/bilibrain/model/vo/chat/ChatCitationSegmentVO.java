package com.bin.bilibrain.model.vo.chat;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatCitationSegmentVO(
    String text,
    List<Integer> sourceRefs
) {
}
