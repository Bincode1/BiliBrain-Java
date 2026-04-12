package com.bin.bilibrain.service.agent;

import com.bin.bilibrain.model.dto.agent.AgentResumeStreamRequest;
import com.bin.bilibrain.model.dto.agent.AgentStreamRequest;
import com.bin.bilibrain.model.dto.chat.CreateConversationRequest;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.model.vo.chat.ChatMessageVO;
import com.bin.bilibrain.service.chat.ConversationService;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SkillAgentSseService {
    private final ConversationService conversationService;
    private final UnifiedAgentService unifiedAgentService;
    private final AgentResumeService agentResumeService;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public SkillAgentSseService(
        ConversationService conversationService,
        UnifiedAgentService unifiedAgentService,
        AgentResumeService agentResumeService,
        ObjectMapper objectMapper,
        @Qualifier("agentTaskExecutor") Executor executor
    ) {
        this.conversationService = conversationService;
        this.unifiedAgentService = unifiedAgentService;
        this.agentResumeService = agentResumeService;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public SseEmitter stream(AgentStreamRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        executor.execute(() -> doStream(request, emitter));
        return emitter;
    }

    public SseEmitter resume(AgentResumeStreamRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        executor.execute(() -> {
            try {
            ChatConversationVO conversation = conversationService.getConversation(request.conversationId());
            send(emitter, "conversation", Map.of("created", false, "conversation", conversation));
            send(emitter, "status", Map.of("stage", "resuming", "message", "正在恢复统一 Agent 执行"));

            AgentExecutionResult result = agentResumeService.resume(
                conversation.id(),
                conversation.folderId(),
                conversation.videoBvid(),
                request
            );
            emitResult(emitter, conversation, result);
            } catch (Exception exception) {
                completeWithError(emitter, exception);
            }
        });
        return emitter;
    }

    private void doStream(AgentStreamRequest request, SseEmitter emitter) {
        try {
            boolean created = !StringUtils.hasText(request.conversationId());
            ChatConversationVO conversation = created
                ? conversationService.createConversation(new CreateConversationRequest(
                    buildTitleHint(request.message()),
                    "AGENT",
                    request.folderId(),
                    request.videoBvid()
                ))
                : conversationService.getConversation(request.conversationId());

            send(emitter, "conversation", Map.of("created", created, "conversation", conversation));
            conversationService.appendMessage(conversation.id(), "USER", request.message(), "[]");
            send(emitter, "status", Map.of("stage", "started", "message", "统一 Agent 正在分析技能、工具与知识范围"));

            UnifiedAgentService.AgentStreamRuntime runtime = unifiedAgentService.createStreamRuntime(
                conversation.id(),
                conversation.folderId(),
                conversation.videoBvid()
            );
            if (runtime == null) {
                AgentExecutionResult result = unifiedAgentService.execute(
                    conversation.id(),
                    conversation.folderId(),
                    conversation.videoBvid(),
                    request.message()
                );
                emitResult(emitter, conversation, result);
                return;
            }

            send(emitter, "skills", Map.of("items", runtime.activeSkills()));
            AtomicReference<NodeOutput> lastOutputRef = new AtomicReference<>();
            AtomicInteger sentSkillEvents = new AtomicInteger(0);
            AtomicInteger sentToolEvents = new AtomicInteger(0);
            AtomicReference<StringBuilder> streamedAnswerRef = new AtomicReference<>(new StringBuilder());

            runtime.agent().stream(request.message(), runtime.config())
                .doOnNext(output -> {
                    lastOutputRef.set(output);
                    emitLiveNodeOutput(emitter, runtime, output, sentSkillEvents, sentToolEvents, streamedAnswerRef.get());
                })
                .blockLast();

            NodeOutput lastOutput = lastOutputRef.get();
            if (lastOutput == null) {
                throw new IllegalStateException("统一 Agent 流式执行没有返回任何输出。");
            }

            AgentExecutionResult result = unifiedAgentService.adaptStreamResult(
                conversation.id(),
                lastOutput,
                runtime.bridge()
            );
            emitResult(emitter, conversation, result, streamedAnswerRef.get().length() > 0);
        } catch (Exception exception) {
            completeWithError(emitter, exception);
        }
    }

    private void emitResult(SseEmitter emitter, ChatConversationVO conversation, AgentExecutionResult result) throws IOException {
        emitResult(emitter, conversation, result, false);
    }

    private void emitResult(
        SseEmitter emitter,
        ChatConversationVO conversation,
        AgentExecutionResult result,
        boolean answerAlreadyStreamed
    ) throws IOException {
        send(emitter, "skills", Map.of("items", result.activeSkills()));
        for (var skillEvent : result.skillEvents()) {
            send(emitter, "skill", skillEvent);
        }
        for (var toolEvent : result.toolEvents()) {
            send(emitter, "tool", toolEvent);
        }
        send(emitter, "reasoning", Map.of("content", result.reasoning()));
        send(emitter, "route", Map.of("route", result.route()));
        send(emitter, "mode", Map.of("mode", result.mode()));
        send(emitter, "sources", Map.of("sources", result.sources()));

        if (result.waitingApproval()) {
            send(emitter, "status", Map.of("stage", "waiting_approval", "message", "工具调用等待人工确认"));
            send(emitter, "approval", result.approval());
            emitter.complete();
            return;
        }

        if (!answerAlreadyStreamed) {
            for (String chunk : splitChunks(result.answer())) {
                send(emitter, "answer", Map.of("delta", chunk));
            }
        }

        ChatMessageVO assistantMessage = toMessageVO(
            conversationService.appendMessage(
                conversation.id(),
                "ASSISTANT",
                result.answer(),
                writeSourcesJson(result),
                result.mode(),
                result.route()
            )
        );
        send(emitter, "answer_normalized", Map.of(
            "content", result.answer(),
            "route", result.route(),
            "mode", result.mode(),
            "sources", result.sources(),
            "message", assistantMessage
        ));
        send(emitter, "done", Map.of("conversation_id", conversation.id()));
        emitter.complete();
    }

    private void emitLiveNodeOutput(
        SseEmitter emitter,
        UnifiedAgentService.AgentStreamRuntime runtime,
        NodeOutput output,
        AtomicInteger sentSkillEvents,
        AtomicInteger sentToolEvents,
        StringBuilder streamedAnswer
    ) {
        try {
            emitPendingSkillAndToolEvents(emitter, runtime, sentSkillEvents, sentToolEvents);

            if (!(output instanceof StreamingOutput<?> streamingOutput)) {
                return;
            }
            if (streamingOutput.getOutputType() != OutputType.AGENT_MODEL_STREAMING) {
                return;
            }
            String delta = streamingOutput.chunk();
            if (delta == null || delta.isBlank()) {
                return;
            }
            streamedAnswer.append(delta);
            send(emitter, "answer", Map.of("delta", delta));
        } catch (IOException exception) {
            throw new IllegalStateException("发送流式输出失败。", exception);
        }
    }

    private void emitPendingSkillAndToolEvents(
        SseEmitter emitter,
        UnifiedAgentService.AgentStreamRuntime runtime,
        AtomicInteger sentSkillEvents,
        AtomicInteger sentToolEvents
    ) throws IOException {
        List<?> skillEvents = runtime.bridge().skillEvents();
        while (sentSkillEvents.get() < skillEvents.size()) {
            send(emitter, "skill", skillEvents.get(sentSkillEvents.getAndIncrement()));
        }

        List<?> toolEvents = runtime.bridge().toolEvents();
        while (sentToolEvents.get() < toolEvents.size()) {
            send(emitter, "tool", toolEvents.get(sentToolEvents.getAndIncrement()));
        }
    }

    private void completeWithError(SseEmitter emitter, Exception exception) {
        try {
            send(emitter, "error", Map.of("message", exception.getMessage()));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }

    private void send(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
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
            return "Agent 对话";
        }
        return normalized.length() <= 24 ? normalized : normalized.substring(0, 24);
    }

    private ChatMessageVO toMessageVO(com.bin.bilibrain.model.entity.ChatMessage message) {
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

    private String writeSourcesJson(AgentExecutionResult result) {
        try {
            return objectMapper.writeValueAsString(result.sources());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 agent sources 失败。", exception);
        }
    }
}
