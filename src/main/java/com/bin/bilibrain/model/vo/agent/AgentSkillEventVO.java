package com.bin.bilibrain.model.vo.agent;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentSkillEventVO(
    String name,
    String description,
    String phase,
    String message
) {
    public AgentSkillEventVO(String name, String description) {
        this(name, description, "loaded", description);
    }
}
