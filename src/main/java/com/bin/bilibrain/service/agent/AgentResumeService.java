package com.bin.bilibrain.service.agent;

import com.bin.bilibrain.model.dto.agent.AgentResumeStreamRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentResumeService {
    private final UnifiedAgentService unifiedAgentService;

    public AgentExecutionResult resume(
        String conversationId,
        Long folderId,
        String videoBvid,
        AgentResumeStreamRequest request
    ) {
        return unifiedAgentService.resume(conversationId, folderId, videoBvid, request);
    }
}
