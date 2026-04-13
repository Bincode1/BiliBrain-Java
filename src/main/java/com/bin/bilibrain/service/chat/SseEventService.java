package com.bin.bilibrain.service.chat;

import com.bin.bilibrain.model.dto.chat.AskRequest;
import com.bin.bilibrain.model.dto.chat.CreateConversationRequest;
import com.bin.bilibrain.model.entity.ChatMessage;
import com.bin.bilibrain.model.vo.chat.AskResponse;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.model.vo.chat.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SseEventService {
    private final ConversationService conversationService;
    private final ChatAnswerService chatAnswerService;

    public AskResponse ask(AskRequest request) {
        PreparedConversation preparedConversation = prepareConversation(request);
        conversationService.appendMessage(preparedConversation.conversation().id(), "USER", request.message(), "[]");
        ChatAnswerResult result = chatAnswerService.answer(
            preparedConversation.conversation().id(),
            preparedConversation.conversation().folderId(),
            preparedConversation.conversation().videoBvid(),
            request.message()
        );
        ChatMessage assistantMessage = conversationService.appendAssistantMessage(
            preparedConversation.conversation().id(),
            result.answer(),
            result.sources(),
            result.mode(),
            result.route(),
            result.reasoning(),
            "",
            List.of(),
            List.of(),
            List.of(),
            null
        );
        return new AskResponse(
            preparedConversation.conversation().id(),
            preparedConversation.conversation(),
            result.answer(),
            result.route(),
            result.mode(),
            result.reasoning(),
            result.sources(),
            toMessageVO(assistantMessage)
        );
    }

    public SseEmitter streamAnswer(AskRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            PreparedConversation preparedConversation = prepareConversation(request);
            boolean created = preparedConversation.created();
            ChatConversationVO conversation = preparedConversation.conversation();

            send(emitter, "conversation", Map.of(
                "created", created,
                "conversation", conversation,
                "conversation_id", conversation.id()
            ));
            conversationService.appendMessage(conversation.id(), "USER", request.message(), "[]");
            String startedMessage = "正在分析检索路由并生成回答";
            send(emitter, "status", Map.of(
                "stage", "started",
                "message", startedMessage,
                "delta", startedMessage
            ));

            ChatAnswerResult result = chatAnswerService.answer(
                conversation.id(),
                conversation.folderId(),
                conversation.videoBvid(),
                request.message()
            );
            send(emitter, "reasoning", Map.of("content", result.reasoning(), "delta", result.reasoning()));
            send(emitter, "route", Map.of("route", result.route(), "route_mode", result.route()));
            send(emitter, "mode", Map.of("mode", result.mode(), "answer_mode", result.mode()));
            send(emitter, "sources", Map.of("sources", result.sources()));

            for (String chunk : splitChunks(result.answer())) {
                send(emitter, "answer", Map.of("delta", chunk));
            }

            ChatMessageVO assistantMessage = toMessageVO(
                conversationService.appendAssistantMessage(
                    conversation.id(),
                    result.answer(),
                    result.sources(),
                    result.mode(),
                    result.route(),
                    result.reasoning(),
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    null
                )
            );
            send(emitter, "answer_normalized", Map.of(
                "content", result.answer(),
                "text", result.answer(),
                "route", result.route(),
                "route_mode", result.route(),
                "mode", result.mode(),
                "answer_mode", result.mode(),
                "sources", result.sources(),
                "message", assistantMessage,
                "conversation_id", conversation.id()
            ));
            send(emitter, "done", Map.of("conversation_id", conversation.id()));
            emitter.complete();
        } catch (Exception exception) {
            try {
                String message = exception == null || exception.getMessage() == null
                    ? "流式回答失败。"
                    : exception.getMessage();
                send(emitter, "error", Map.of("message", message));
            } catch (IOException ignored) {
            }
            emitter.complete();
        }
        return emitter;
    }

    private PreparedConversation prepareConversation(AskRequest request) {
        boolean created = !StringUtils.hasText(request.conversationId());
        ChatConversationVO conversation = created
            ? conversationService.createConversation(new CreateConversationRequest(
                buildTitleHint(request.message()),
                request.conversationType(),
                request.folderId(),
                request.videoBvid()
            ))
            : conversationService.getConversation(request.conversationId());
        return new PreparedConversation(created, conversation);
    }

    private void send(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(
            SseEmitter.event()
                .name(eventName)
                .data(data, MediaType.APPLICATION_JSON)
        );
    }

    private List<String> splitChunks(String answer) {
        int chunkSize = 28;
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        for (int start = 0; start < answer.length(); start += chunkSize) {
            chunks.add(answer.substring(start, Math.min(start + chunkSize, answer.length())));
        }
        return chunks.isEmpty() ? List.of(answer) : chunks;
    }

    private ChatMessageVO toMessageVO(ChatMessage message) {
        return new ChatMessageVO(
            message.getId(),
            message.getConversationId(),
            message.getRole(),
            message.getContent(),
            message.getSourcesJson(),
            message.getAnswerMode(),
            message.getRouteMode(),
            message.getCreatedAt() == null ? "" : message.getCreatedAt().toString()
        );
    }

    private String buildTitleHint(String message) {
        String normalized = message == null ? "" : message.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "新对话";
        }
        return normalized.length() <= 24 ? normalized : normalized.substring(0, 24);
    }

    private record PreparedConversation(boolean created, ChatConversationVO conversation) {
    }
}
