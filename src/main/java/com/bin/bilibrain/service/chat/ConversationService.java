package com.bin.bilibrain.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.mapper.ChatConversationContextStatMapper;
import com.bin.bilibrain.mapper.ChatConversationMapper;
import com.bin.bilibrain.mapper.ChatConversationMemoryMapper;
import com.bin.bilibrain.mapper.ChatMessageMapper;
import com.bin.bilibrain.service.agent.AgentScopeService;
import com.bin.bilibrain.model.dto.chat.CreateConversationRequest;
import com.bin.bilibrain.model.dto.chat.UpdateConversationRequest;
import com.bin.bilibrain.model.entity.ChatConversation;
import com.bin.bilibrain.model.entity.ChatConversationContextStat;
import com.bin.bilibrain.model.entity.ChatConversationMemory;
import com.bin.bilibrain.model.entity.ChatMessage;
import com.bin.bilibrain.model.vo.agent.AgentApprovalVO;
import com.bin.bilibrain.model.vo.agent.AgentSkillEventVO;
import com.bin.bilibrain.model.vo.agent.AgentToolEventVO;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.model.vo.chat.ChatMessageVO;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private static final String DEFAULT_TITLE = "新对话";

    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatConversationMemoryMapper chatConversationMemoryMapper;
    private final ChatConversationContextStatMapper chatConversationContextStatMapper;
    private final ConversationMemoryService conversationMemoryService;
    private final AgentScopeService agentScopeService;
    private final ObjectMapper objectMapper;

    public List<ChatConversationVO> listConversations() {
        return chatConversationMapper.selectList(
                new LambdaQueryWrapper<ChatConversation>()
                    .orderByDesc(ChatConversation::getUpdatedAt)
            ).stream()
            .map(this::toConversationVO)
            .toList();
    }

    public ChatConversationVO createConversation(CreateConversationRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = ChatConversation.builder()
            .title(defaultTitle(request.title()))
            .conversationType(defaultConversationType(request.conversationType()))
            .folderId(request.folderId())
            .videoBvid(blankToNull(request.videoBvid()))
            .createdAt(now)
            .updatedAt(now)
            .build();
        chatConversationMapper.insert(conversation);

        chatConversationMemoryMapper.insert(ChatConversationMemory.builder()
            .conversationId(conversation.getId())
            .memoryText("")
            .sourceMessageCount(0)
            .updatedAt(now)
            .build());

        chatConversationContextStatMapper.insert(ChatConversationContextStat.builder()
            .conversationId(conversation.getId())
            .totalMessages(0)
            .promptTokens(0)
            .completionTokens(0)
            .updatedAt(now)
            .build());
        return toConversationVO(requireConversation(conversation.getId()));
    }

    public ChatConversationVO getConversation(String conversationId) {
        return toConversationVO(requireConversation(conversationId));
    }

    public ChatConversationVO ensureConversation(
        String conversationId,
        String titleHint,
        String conversationType,
        Long folderId,
        String videoBvid,
        String scopeMode
    ) {
        if (!StringUtils.hasText(conversationId)) {
            AgentScopeService.ScopeSelection scope = agentScopeService.resolveScope(scopeMode, folderId, videoBvid);
            return createConversation(new CreateConversationRequest(
                titleHint,
                conversationType,
                scope.folderId(),
                scope.videoBvid()
            ));
        }

        ChatConversation conversation = requireConversation(conversationId);
        return toConversationVO(conversation);
    }

    public ChatConversationVO updateConversation(String conversationId, UpdateConversationRequest request) {
        ChatConversation conversation = requireConversation(conversationId);
        conversation.setTitle(request.title().trim());
        conversation.setUpdatedAt(LocalDateTime.now());
        chatConversationMapper.updateById(conversation);
        return toConversationVO(requireConversation(conversationId));
    }

    public void deleteConversation(String conversationId) {
        requireConversation(conversationId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getConversationId, conversationId));
        chatConversationMemoryMapper.deleteById(conversationId);
        chatConversationContextStatMapper.deleteById(conversationId);
        chatConversationMapper.deleteById(conversationId);
    }

    public List<ChatMessageVO> getHistory(String conversationId) {
        requireConversation(conversationId);
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, conversationId)
                    .orderByAsc(ChatMessage::getCreatedAt)
                    .orderByAsc(ChatMessage::getId)
            ).stream()
            .map(this::toMessageVO)
            .toList();
    }

    public ChatMessage appendMessage(String conversationId, String role, String content, String sourcesJson) {
        return appendMessage(conversationId, role, content, sourcesJson, "", "");
    }

    public ChatMessage appendMessage(
        String conversationId,
        String role,
        String content,
        String sourcesJson,
        String answerMode,
        String routeMode
    ) {
        return appendMessageInternal(
            conversationId,
            role,
            content,
            sourcesJson,
            answerMode,
            routeMode,
            "",
            "",
            "[]",
            "[]",
            "[]",
            ""
        );
    }

    public ChatMessage appendAssistantMessage(
        String conversationId,
        String content,
        List<ChatSourceVO> sources,
        String answerMode,
        String routeMode,
        String reasoningText,
        String agentStatus,
        List<AgentSkillEventVO> skillEvents,
        List<AgentToolEventVO> toolEvents,
        List<SkillListItemVO> activeSkills,
        AgentApprovalVO approval
    ) {
        return appendMessageInternal(
            conversationId,
            "ASSISTANT",
            content,
            writeJson(sources, "[]"),
            answerMode,
            routeMode,
            reasoningText,
            agentStatus,
            writeJson(skillEvents, "[]"),
            writeJson(toolEvents, "[]"),
            writeJson(activeSkills, "[]"),
            approval == null ? "" : writeJson(approval, "")
        );
    }

    public ChatMessage upsertPendingApprovalAssistantMessage(
        String conversationId,
        String content,
        List<ChatSourceVO> sources,
        String answerMode,
        String routeMode,
        String reasoningText,
        String agentStatus,
        List<AgentSkillEventVO> skillEvents,
        List<AgentToolEventVO> toolEvents,
        List<SkillListItemVO> activeSkills,
        AgentApprovalVO approval
    ) {
        ChatMessage pendingMessage = findLatestPendingApprovalMessage(conversationId);
        if (pendingMessage == null) {
            return appendAssistantMessage(
                conversationId,
                content,
                sources,
                answerMode,
                routeMode,
                reasoningText,
                agentStatus,
                skillEvents,
                toolEvents,
                activeSkills,
                approval
            );
        }
        return updateAssistantMessage(
            pendingMessage,
            content,
            sources,
            answerMode,
            routeMode,
            reasoningText,
            agentStatus,
            skillEvents,
            toolEvents,
            activeSkills,
            approval
        );
    }

    public ChatMessage finalizePendingApprovalAssistantMessage(
        String conversationId,
        String content,
        List<ChatSourceVO> sources,
        String answerMode,
        String routeMode,
        String reasoningText,
        List<AgentSkillEventVO> skillEvents,
        List<AgentToolEventVO> toolEvents,
        List<SkillListItemVO> activeSkills
    ) {
        ChatMessage pendingMessage = findLatestPendingApprovalMessage(conversationId);
        if (pendingMessage == null) {
            return appendAssistantMessage(
                conversationId,
                content,
                sources,
                answerMode,
                routeMode,
                reasoningText,
                "",
                skillEvents,
                toolEvents,
                activeSkills,
                null
            );
        }
        List<ChatSourceVO> mergedSources = mergeUnique(
            readList(pendingMessage.getSourcesJson(), ChatSourceVO.class),
            sources
        );
        List<AgentSkillEventVO> mergedSkillEvents = mergeUnique(
            readList(pendingMessage.getSkillEventsJson(), AgentSkillEventVO.class),
            skillEvents
        );
        List<AgentToolEventVO> mergedToolEvents = mergeUnique(
            readList(pendingMessage.getToolEventsJson(), AgentToolEventVO.class),
            toolEvents
        );
        List<SkillListItemVO> mergedActiveSkills = mergeUnique(
            readList(pendingMessage.getActiveSkillsJson(), SkillListItemVO.class),
            activeSkills
        );
        return updateAssistantMessage(
            pendingMessage,
            content,
            mergedSources,
            answerMode,
            routeMode,
            mergeText(pendingMessage.getReasoningText(), reasoningText),
            "",
            mergedSkillEvents,
            mergedToolEvents,
            mergedActiveSkills,
            null
        );
    }

    public ChatMessageVO toMessageVO(ChatMessage message) {
        return new ChatMessageVO(
            message.getId(),
            message.getConversationId(),
            message.getRole(),
            message.getContent(),
            safeJson(message.getSourcesJson()),
            readList(message.getSourcesJson(), ChatSourceVO.class),
            blankToEmpty(message.getAnswerMode()),
            blankToEmpty(message.getRouteMode()),
            blankToEmpty(message.getReasoningText()),
            blankToEmpty(message.getAgentStatus()),
            readList(message.getSkillEventsJson(), AgentSkillEventVO.class),
            readList(message.getToolEventsJson(), AgentToolEventVO.class),
            readList(message.getActiveSkillsJson(), SkillListItemVO.class),
            readObject(message.getApprovalJson(), AgentApprovalVO.class),
            formatDateTime(message.getCreatedAt())
        );
    }

    private ChatMessage appendMessageInternal(
        String conversationId,
        String role,
        String content,
        String sourcesJson,
        String answerMode,
        String routeMode,
        String reasoningText,
        String agentStatus,
        String skillEventsJson,
        String toolEventsJson,
        String activeSkillsJson,
        String approvalJson
    ) {
        ChatConversation conversation = requireConversation(conversationId);
        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = ChatMessage.builder()
            .conversationId(conversationId)
            .role(role)
            .content(content)
            .sourcesJson(safeJson(sourcesJson))
            .answerMode(blankToEmpty(answerMode))
            .routeMode(blankToEmpty(routeMode))
            .reasoningText(blankToEmpty(reasoningText))
            .agentStatus(blankToEmpty(agentStatus))
            .skillEventsJson(safeArrayJson(skillEventsJson))
            .toolEventsJson(safeArrayJson(toolEventsJson))
            .activeSkillsJson(safeArrayJson(activeSkillsJson))
            .approvalJson(blankToEmpty(approvalJson))
            .createdAt(now)
            .build();
        chatMessageMapper.insert(message);

        chatConversationMapper.update(
            null,
            new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .set(ChatConversation::getUpdatedAt, now)
        );

        if ("USER".equalsIgnoreCase(role) && DEFAULT_TITLE.equals(conversation.getTitle())) {
            conversation.setTitle(buildTitleFromMessage(content));
            conversation.setUpdatedAt(now);
            chatConversationMapper.updateById(conversation);
        }

        refreshContextStats(conversationId);
        conversationMemoryService.compactIfNeeded(conversationId);
        return message;
    }

    private ChatMessage updateAssistantMessage(
        ChatMessage message,
        String content,
        List<ChatSourceVO> sources,
        String answerMode,
        String routeMode,
        String reasoningText,
        String agentStatus,
        List<AgentSkillEventVO> skillEvents,
        List<AgentToolEventVO> toolEvents,
        List<SkillListItemVO> activeSkills,
        AgentApprovalVO approval
    ) {
        requireConversation(message.getConversationId());
        LocalDateTime now = LocalDateTime.now();
        message.setContent(content);
        message.setSourcesJson(writeJson(sources, "[]"));
        message.setAnswerMode(blankToEmpty(answerMode));
        message.setRouteMode(blankToEmpty(routeMode));
        message.setReasoningText(blankToEmpty(reasoningText));
        message.setAgentStatus(blankToEmpty(agentStatus));
        message.setSkillEventsJson(writeJson(skillEvents, "[]"));
        message.setToolEventsJson(writeJson(toolEvents, "[]"));
        message.setActiveSkillsJson(writeJson(activeSkills, "[]"));
        message.setApprovalJson(approval == null ? "" : writeJson(approval, ""));
        chatMessageMapper.updateById(message);

        chatConversationMapper.update(
            null,
            new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, message.getConversationId())
                .set(ChatConversation::getUpdatedAt, now)
        );

        refreshContextStats(message.getConversationId());
        conversationMemoryService.compactIfNeeded(message.getConversationId());
        return message;
    }

    private ChatMessage findLatestPendingApprovalMessage(String conversationId) {
        return chatMessageMapper.selectOne(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getRole, "ASSISTANT")
                .ne(ChatMessage::getApprovalJson, "")
                .orderByDesc(ChatMessage::getCreatedAt)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1")
        );
    }

    private ChatConversation requireConversation(String conversationId) {
        ChatConversation conversation = chatConversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "找不到这个会话。", HttpStatus.NOT_FOUND);
        }
        return conversation;
    }

    private void refreshContextStats(String conversationId) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getConversationId, conversationId)
        );
        int promptTokens = messages.stream()
            .filter(message -> "USER".equalsIgnoreCase(message.getRole()))
            .map(ChatMessage::getContent)
            .mapToInt(this::estimateTokens)
            .sum();
        int completionTokens = messages.stream()
            .filter(message -> "ASSISTANT".equalsIgnoreCase(message.getRole()))
            .map(ChatMessage::getContent)
            .mapToInt(this::estimateTokens)
            .sum();

        ChatConversationContextStat stat = ChatConversationContextStat.builder()
            .conversationId(conversationId)
            .totalMessages(messages.size())
            .promptTokens(promptTokens)
            .completionTokens(completionTokens)
            .updatedAt(LocalDateTime.now())
            .build();

        if (chatConversationContextStatMapper.selectById(conversationId) == null) {
            chatConversationContextStatMapper.insert(stat);
        } else {
            chatConversationContextStatMapper.updateById(stat);
        }
    }

    private ChatConversationVO toConversationVO(ChatConversation conversation) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .orderByDesc(ChatMessage::getCreatedAt)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1")
        );
        long count = chatMessageMapper.selectCount(
            new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getConversationId, conversation.getId())
        );
        String preview = messages.isEmpty() ? "" : preview(messages.get(0).getContent());
        return new ChatConversationVO(
            conversation.getId(),
            conversation.getTitle(),
            conversation.getConversationType(),
            conversation.getFolderId(),
            nullToBlank(conversation.getVideoBvid()),
            Math.toIntExact(count),
            preview,
            formatDateTime(conversation.getUpdatedAt())
        );
    }

    private String defaultTitle(String title) {
        return StringUtils.hasText(title) ? title.trim() : DEFAULT_TITLE;
    }

    private String defaultConversationType(String conversationType) {
        return StringUtils.hasText(conversationType) ? conversationType.trim().toUpperCase() : "GENERAL";
    }

    private String buildTitleFromMessage(String content) {
        String normalized = content == null ? DEFAULT_TITLE : content.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return DEFAULT_TITLE;
        }
        return normalized.length() <= 24 ? normalized : normalized.substring(0, 24);
    }

    private int estimateTokens(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return Math.max(1, content.trim().length() / 4);
    }

    private String preview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
    }

    private String mergeText(String existing, String incoming) {
        String left = blankToEmpty(existing);
        String right = blankToEmpty(incoming);
        if (!StringUtils.hasText(left)) {
            return right;
        }
        if (!StringUtils.hasText(right) || left.equals(right)) {
            return left;
        }
        if (right.startsWith(left)) {
            return right;
        }
        return left + "\n\n" + right;
    }

    private <T> List<T> mergeUnique(List<T> existing, List<T> incoming) {
        LinkedHashSet<T> merged = new LinkedHashSet<>();
        if (existing != null) {
            merged.addAll(existing);
        }
        if (incoming != null) {
            merged.addAll(incoming);
        }
        return new ArrayList<>(merged);
    }

    private String safeJson(String value) {
        return StringUtils.hasText(value) ? value : "[]";
    }

    private String safeArrayJson(String value) {
        return StringUtils.hasText(value) ? value : "[]";
    }

    private String blankToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String writeJson(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return fallback;
        }
    }

    private <T> List<T> readList(String value, Class<T> itemType) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        JavaType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, itemType);
        try {
            return objectMapper.readValue(value, javaType);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private <T> T readObject(String value, Class<T> targetType) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, targetType);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
