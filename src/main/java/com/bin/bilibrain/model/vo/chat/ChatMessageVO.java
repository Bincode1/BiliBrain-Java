package com.bin.bilibrain.model.vo.chat;

import com.bin.bilibrain.model.vo.agent.AgentApprovalVO;
import com.bin.bilibrain.model.vo.agent.AgentSkillEventVO;
import com.bin.bilibrain.model.vo.agent.AgentToolEventVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatMessageVO(
    Long id,
    String conversationId,
    String role,
    String content,
    List<ChatSourceVO> sources,
    List<ChatCitationSegmentVO> citationSegments,
    String answerMode,
    String routeMode,
    String reasoningText,
    String agentStatus,
    List<AgentSkillEventVO> skillEvents,
    List<AgentToolEventVO> toolEvents,
    List<SkillListItemVO> activeSkills,
    AgentApprovalVO approval,
    String createdAt
) {
    public ChatMessageVO(
        Long id,
        String conversationId,
        String role,
        String content,
        String answerMode,
        String routeMode,
        String createdAt
    ) {
        this(
            id,
            conversationId,
            role,
            content,
            List.of(),
            List.of(),
            answerMode,
            routeMode,
            "",
            "",
            List.of(),
            List.of(),
            List.of(),
            null,
            createdAt
        );
    }
}
