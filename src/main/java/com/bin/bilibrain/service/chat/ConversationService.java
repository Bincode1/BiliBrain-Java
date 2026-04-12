package com.bin.bilibrain.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.mapper.ChatConversationContextStatMapper;
import com.bin.bilibrain.mapper.ChatConversationMapper;
import com.bin.bilibrain.mapper.ChatConversationMemoryMapper;
import com.bin.bilibrain.mapper.ChatMessageMapper;
import com.bin.bilibrain.model.dto.chat.CreateConversationRequest;
import com.bin.bilibrain.model.dto.chat.UpdateConversationRequest;
import com.bin.bilibrain.model.entity.ChatConversation;
import com.bin.bilibrain.model.entity.ChatConversationContextStat;
import com.bin.bilibrain.model.entity.ChatConversationMemory;
import com.bin.bilibrain.model.entity.ChatMessage;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.model.vo.chat.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private static final String DEFAULT_TITLE = "新对话";

    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatConversationMemoryMapper chatConversationMemoryMapper;
    private final ChatConversationContextStatMapper chatConversationContextStatMapper;

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
        ChatConversation conversation = requireConversation(conversationId);
        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = ChatMessage.builder()
            .conversationId(conversationId)
            .role(role)
            .content(content)
            .sourcesJson(safeJson(sourcesJson))
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
        return message;
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
            nullToBlank(conversation.getVideoBvid()),
            Math.toIntExact(count),
            preview,
            formatDateTime(conversation.getUpdatedAt())
        );
    }

    private ChatMessageVO toMessageVO(ChatMessage message) {
        return new ChatMessageVO(
            message.getId(),
            message.getConversationId(),
            message.getRole(),
            message.getContent(),
            formatDateTime(message.getCreatedAt())
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

    private String safeJson(String value) {
        return StringUtils.hasText(value) ? value : "[]";
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
}
