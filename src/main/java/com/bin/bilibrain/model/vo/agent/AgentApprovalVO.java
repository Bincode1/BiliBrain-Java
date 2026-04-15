package com.bin.bilibrain.model.vo.agent;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentApprovalVO(
    String conversationId,
    String scopeMode,
    Long folderId,
    String videoBvid,
    List<AgentApprovalItemVO> items
) {
}
