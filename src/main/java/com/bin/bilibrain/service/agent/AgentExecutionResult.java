package com.bin.bilibrain.service.agent;

import com.bin.bilibrain.model.vo.agent.AgentApprovalVO;
import com.bin.bilibrain.model.vo.agent.AgentSkillEventVO;
import com.bin.bilibrain.model.vo.agent.AgentToolEventVO;
import com.bin.bilibrain.model.vo.chat.ChatCitationSegmentVO;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;

import java.util.List;

public record AgentExecutionResult(
    String answer,
    String route,
    String mode,
    String reasoning,
    List<ChatSourceVO> sources,
    List<ChatCitationSegmentVO> citationSegments,
    List<SkillListItemVO> activeSkills,
    List<AgentSkillEventVO> skillEvents,
    List<AgentToolEventVO> toolEvents,
    AgentApprovalVO approval
) {
    public AgentExecutionResult(
        String answer,
        String route,
        String mode,
        String reasoning,
        List<ChatSourceVO> sources,
        List<SkillListItemVO> activeSkills,
        List<AgentSkillEventVO> skillEvents,
        List<AgentToolEventVO> toolEvents,
        AgentApprovalVO approval
    ) {
        this(answer, route, mode, reasoning, sources, List.of(), activeSkills, skillEvents, toolEvents, approval);
    }

    public boolean waitingApproval() {
        return approval != null;
    }
}
