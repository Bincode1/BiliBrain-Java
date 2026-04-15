package com.bin.bilibrain.stream.event;

import com.bin.bilibrain.model.vo.agent.AgentApprovalVO;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.model.vo.chat.ChatMessageVO;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import com.bin.bilibrain.service.agent.AgentExecutionResult;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SseEventBuilder {

    public ServerSentEvent<Object> conversation(boolean created, ChatConversationVO conversation) {
        return event("conversation", Map.of(
            "created", created,
            "conversation", conversation,
            "conversation_id", conversation.id()
        ));
    }

    public ServerSentEvent<Object> status(String stage, String message) {
        return event("status", Map.of(
            "stage", stage,
            "message", message,
            "delta", message
        ));
    }

    public ServerSentEvent<Object> answer(String delta) {
        return event("answer", Map.of("delta", delta));
    }

    public ServerSentEvent<Object> answerNormalized(
        ChatConversationVO conversation,
        AgentExecutionResult result,
        ChatMessageVO message
    ) {
        return event("answer_normalized", Map.of(
            "route", result.route(),
            "route_mode", result.route(),
            "mode", result.mode(),
            "answer_mode", result.mode(),
            "sources", result.sources(),
            "message", messageMetadata(message),
            "conversation_id", conversation.id()
        ));
    }

    private Map<String, Object> messageMetadata(ChatMessageVO message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", message.id());
        payload.put("conversation_id", message.conversationId());
        payload.put("role", message.role());
        payload.put("answer_mode", message.answerMode());
        payload.put("route_mode", message.routeMode());
        payload.put("reasoning_text", message.reasoningText());
        payload.put("agent_status", message.agentStatus());
        payload.put("sources", message.sources());
        payload.put("skill_events", message.skillEvents());
        payload.put("tool_events", message.toolEvents());
        payload.put("active_skills", message.activeSkills());
        payload.put("approval", message.approval());
        payload.put("created_at", message.createdAt());
        return payload;
    }

    public ServerSentEvent<Object> sources(List<ChatSourceVO> sources) {
        return event("sources", Map.of("sources", sources));
    }

    public ServerSentEvent<Object> reasoning(String reasoning) {
        return event("reasoning", Map.of("content", reasoning, "delta", reasoning));
    }

    public ServerSentEvent<Object> route(String route) {
        return event("route", Map.of("route", route, "route_mode", route));
    }

    public ServerSentEvent<Object> mode(String mode) {
        return event("mode", Map.of("mode", mode, "answer_mode", mode));
    }

    public ServerSentEvent<Object> skills(List<SkillListItemVO> activeSkills) {
        return event("skills", Map.of(
            "items", activeSkills,
            "active_skills", activeSkills
        ));
    }

    public ServerSentEvent<Object> approval(AgentApprovalVO approval) {
        return event("approval", approval);
    }

    public ServerSentEvent<Object> done(String conversationId) {
        return event("done", Map.of("conversation_id", conversationId));
    }

    public ServerSentEvent<Object> error(String message) {
        return event("error", Map.of("message", message));
    }

    public ServerSentEvent<Object> event(String eventName, Object data) {
        return ServerSentEvent.builder(data)
            .event(eventName)
            .build();
    }
}
