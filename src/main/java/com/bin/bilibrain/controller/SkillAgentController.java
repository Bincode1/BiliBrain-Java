package com.bin.bilibrain.controller;

import com.bin.bilibrain.model.dto.agent.AgentResumeStreamRequest;
import com.bin.bilibrain.model.dto.agent.AgentStreamRequest;
import com.bin.bilibrain.service.agent.SkillAgentSseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/skill-agent")
@RequiredArgsConstructor
public class SkillAgentController {
    private final SkillAgentSseService skillAgentSseService;

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AgentStreamRequest request) {
        return skillAgentSseService.stream(request);
    }

    @PostMapping(path = "/resume/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(@Valid @RequestBody AgentResumeStreamRequest request) {
        return skillAgentSseService.resume(request);
    }
}
