package com.bin.bilibrain.service.chat;

import com.bin.bilibrain.model.dto.chat.AskStreamRequest;
import com.bin.bilibrain.model.dto.chat.CreateConversationRequest;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.model.vo.chat.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SseEventService {
    private final ConversationService conversationService;

    public SseEmitter streamDirectAnswer(AskStreamRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            boolean created = !StringUtils.hasText(request.conversationId());
            ChatConversationVO conversation = created
                ? conversationService.createConversation(new CreateConversationRequest(
                    buildTitleHint(request.message()),
                    request.conversationType(),
                    request.videoBvid()
                ))
                : conversationService.getConversation(request.conversationId());

            send(emitter, "conversation", Map.of(
                "created", created,
                "conversation", conversation
            ));
            conversationService.appendMessage(conversation.id(), "USER", request.message(), "[]");
            send(emitter, "status", Map.of(
                "stage", "started",
                "message", "正在生成 direct mode 回答"
            ));

            String answer = buildDirectAnswer(request, conversation);
            for (String chunk : splitChunks(answer)) {
                send(emitter, "answer", Map.of("delta", chunk));
            }

            ChatMessageVO assistantMessage = toMessageVO(
                conversationService.appendMessage(conversation.id(), "ASSISTANT", answer, "[]")
            );
            send(emitter, "answer_normalized", Map.of(
                "content", answer,
                "message", assistantMessage
            ));
            send(emitter, "done", Map.of("conversation_id", conversation.id()));
            emitter.complete();
        } catch (Exception exception) {
            try {
                send(emitter, "error", Map.of("message", exception.getMessage()));
            } catch (IOException ignored) {
            }
            emitter.complete();
        }
        return emitter;
    }

    private void send(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(
            SseEmitter.event()
                .name(eventName)
                .data(data, MediaType.APPLICATION_JSON)
        );
    }

    private String buildDirectAnswer(AskStreamRequest request, ChatConversationVO conversation) {
        StringBuilder builder = new StringBuilder("当前聊天主链已经完成会话与 SSE 外壳接入。");
        builder.append(" 这一轮先返回 direct mode 占位回答，下一阶段会接入知识检索和正式模型问答。");
        if (StringUtils.hasText(request.videoBvid())) {
            builder.append(" 当前会话绑定视频 ").append(request.videoBvid()).append("。");
        }
        builder.append("\n\n问题：").append(request.message().trim());
        builder.append("\n\n会话：").append(conversation.title());
        return builder.toString();
    }

    private List<String> splitChunks(String answer) {
        int chunkSize = 28;
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        for (int start = 0; start < answer.length(); start += chunkSize) {
            chunks.add(answer.substring(start, Math.min(start + chunkSize, answer.length())));
        }
        return chunks.isEmpty() ? List.of(answer) : chunks;
    }

    private String buildTitleHint(String message) {
        String normalized = message == null ? "" : message.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "新对话";
        }
        return normalized.length() <= 24 ? normalized : normalized.substring(0, 24);
    }

    private ChatMessageVO toMessageVO(com.bin.bilibrain.model.entity.ChatMessage message) {
        return new ChatMessageVO(
            message.getId(),
            message.getConversationId(),
            message.getRole(),
            message.getContent(),
            message.getCreatedAt() == null ? "" : message.getCreatedAt().toString()
        );
    }
}
