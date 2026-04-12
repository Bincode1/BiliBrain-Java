package com.bin.bilibrain.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.mapper.ChatConversationContextStatMapper;
import com.bin.bilibrain.mapper.ChatConversationMemoryMapper;
import com.bin.bilibrain.mapper.ChatMessageMapper;
import com.bin.bilibrain.model.entity.ChatConversationContextStat;
import com.bin.bilibrain.model.entity.ChatConversationMemory;
import com.bin.bilibrain.model.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationMemoryService {
    private final ChatMessageMapper chatMessageMapper;
    private final ChatConversationMemoryMapper chatConversationMemoryMapper;
    private final ChatConversationContextStatMapper chatConversationContextStatMapper;
    private final AppProperties appProperties;

    public void compactIfNeeded(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        ChatConversationContextStat contextStat = chatConversationContextStatMapper.selectById(conversationId);
        if (contextStat == null) {
            return;
        }
        int totalTokens = safe(contextStat.getPromptTokens()) + safe(contextStat.getCompletionTokens());
        if (totalTokens < appProperties.getChat().getCompactionTokenThreshold()) {
            return;
        }

        List<ChatMessage> messages = listMessages(conversationId);
        int recentLimit = appProperties.getChat().getRecentMessageLimit();
        int compactUntil = Math.max(0, messages.size() - recentLimit);
        if (compactUntil <= 0) {
            return;
        }

        ChatConversationMemory memory = ensureMemory(conversationId);
        int alreadyCompacted = safe(memory.getSourceMessageCount());
        if (compactUntil <= alreadyCompacted) {
            return;
        }

        List<ChatMessage> deltaMessages = messages.subList(alreadyCompacted, compactUntil);
        String deltaSummary = summarizeMessages(deltaMessages);
        if (!StringUtils.hasText(deltaSummary)) {
            return;
        }

        memory.setMemoryText(mergeMemory(memory.getMemoryText(), deltaSummary));
        memory.setSourceMessageCount(compactUntil);
        memory.setUpdatedAt(LocalDateTime.now());
        chatConversationMemoryMapper.updateById(memory);
    }

    public String decoratePrompt(String conversationId, String userPrompt) {
        if (!StringUtils.hasText(conversationId)) {
            return userPrompt;
        }
        ChatConversationMemory memory = chatConversationMemoryMapper.selectById(conversationId);
        List<ChatMessage> recentMessages = recentMessages(conversationId, true);
        boolean hasMemory = memory != null && StringUtils.hasText(memory.getMemoryText());
        boolean hasRecentMessages = !recentMessages.isEmpty();
        if (!hasMemory && !hasRecentMessages) {
            return userPrompt;
        }

        StringBuilder builder = new StringBuilder();
        if (hasMemory) {
            builder.append("已压缩历史记忆：\n")
                .append(memory.getMemoryText().trim())
                .append("\n\n");
        }
        if (hasRecentMessages) {
            builder.append("最近对话（按时间顺序）：\n")
                .append(renderMessages(recentMessages))
                .append("\n\n");
        }
        builder.append("当前请求：\n").append(userPrompt);
        return builder.toString();
    }

    private List<ChatMessage> recentMessages(String conversationId, boolean excludeLatestUserMessage) {
        List<ChatMessage> messages = listMessages(conversationId);
        if (excludeLatestUserMessage && !messages.isEmpty()) {
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            if ("USER".equalsIgnoreCase(lastMessage.getRole())) {
                messages = messages.subList(0, messages.size() - 1);
            }
        }
        if (messages.isEmpty()) {
            return List.of();
        }

        ChatConversationMemory memory = chatConversationMemoryMapper.selectById(conversationId);
        int compactedCount = memory == null ? 0 : safe(memory.getSourceMessageCount());
        if (compactedCount >= messages.size()) {
            return List.of();
        }
        int startIndex = Math.max(compactedCount, messages.size() - appProperties.getChat().getRecentMessageLimit());
        return messages.subList(startIndex, messages.size());
    }

    private List<ChatMessage> listMessages(String conversationId) {
        return chatMessageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreatedAt)
                .orderByAsc(ChatMessage::getId)
        );
    }

    private ChatConversationMemory ensureMemory(String conversationId) {
        ChatConversationMemory memory = chatConversationMemoryMapper.selectById(conversationId);
        if (memory != null) {
            return memory;
        }
        ChatConversationMemory created = ChatConversationMemory.builder()
            .conversationId(conversationId)
            .memoryText("")
            .sourceMessageCount(0)
            .updatedAt(LocalDateTime.now())
            .build();
        chatConversationMemoryMapper.insert(created);
        return created;
    }

    private String summarizeMessages(List<ChatMessage> messages) {
        return messages.stream()
            .map(this::toMemoryLine)
            .filter(StringUtils::hasText)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }

    private String renderMessages(List<ChatMessage> messages) {
        return messages.stream()
            .map(message -> "[%s] %s".formatted(normalizeRole(message.getRole()), normalizeText(message.getContent())))
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }

    private String toMemoryLine(ChatMessage message) {
        String content = normalizeText(message.getContent());
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return "- %s: %s".formatted(normalizeRole(message.getRole()), content);
    }

    private String mergeMemory(String existing, String delta) {
        String merged = StringUtils.hasText(existing) ? existing.trim() + "\n" + delta.trim() : delta.trim();
        int maxCharacters = appProperties.getChat().getMemoryMaxCharacters();
        if (merged.length() <= maxCharacters) {
            return merged;
        }
        return "..." + merged.substring(merged.length() - maxCharacters + 3);
    }

    private String normalizeRole(String role) {
        return StringUtils.hasText(role) ? role.trim().toUpperCase() : "UNKNOWN";
    }

    private String normalizeText(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        int maxCharacters = appProperties.getChat().getMemoryLineMaxCharacters();
        if (normalized.length() <= maxCharacters) {
            return normalized;
        }
        return normalized.substring(0, maxCharacters - 3) + "...";
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
