package com.bin.bilibrain.model.vo.agent;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentToolEventVO(
    String name,
    Object summary,
    String phase,
    Object result,
    Boolean ok,
    String error,
    Long workspaceId
) {
    public AgentToolEventVO(String name, String summary) {
        this(name, summary, "finish", null, true, "", null);
    }

    public static AgentToolEventVO start(String name, Object summary) {
        return new AgentToolEventVO(name, summary, "start", null, null, "", null);
    }

    public static AgentToolEventVO finish(String name, Object summary, Object result) {
        return new AgentToolEventVO(name, summary, "finish", result, true, "", null);
    }

    public static AgentToolEventVO failed(String name, Object summary, String error) {
        return new AgentToolEventVO(name, summary, "finish", null, false, error == null ? "" : error, null);
    }
}
