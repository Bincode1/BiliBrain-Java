package com.bin.bilibrain.service.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.bin.bilibrain.model.dto.agent.AgentResumeStreamRequest;
import com.bin.bilibrain.model.dto.agent.AgentStreamRequest;
import com.bin.bilibrain.model.vo.chat.ChatConversationVO;
import com.bin.bilibrain.model.vo.chat.ChatMessageVO;
import com.bin.bilibrain.service.chat.ConversationService;
import com.bin.bilibrain.stream.event.SseEventBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class SkillAgentSseService {
    private final ConversationService conversationService;
    private final UnifiedAgentService unifiedAgentService;
    private final AgentResumeService agentResumeService;
    private final SseEventBuilder sseEventBuilder;
    private final Scheduler streamScheduler;

    public SkillAgentSseService(
        ConversationService conversationService,
        UnifiedAgentService unifiedAgentService,
        AgentResumeService agentResumeService,
        SseEventBuilder sseEventBuilder,
        @Qualifier("agentTaskExecutor") ObjectProvider<Executor> executorProvider
    ) {
        this.conversationService = conversationService;
        this.unifiedAgentService = unifiedAgentService;
        this.agentResumeService = agentResumeService;
        this.sseEventBuilder = sseEventBuilder;
        Executor executor = executorProvider.getIfAvailable();
        this.streamScheduler = executor == null ? Schedulers.immediate() : Schedulers.fromExecutor(executor);
    }

    public Flux<ServerSentEvent<Object>> stream(AgentStreamRequest request) {
        return Flux.defer(() -> buildStream(request))
            .subscribeOn(streamScheduler)
            .doOnSubscribe(ignored -> log.info(
                "agent stream started: conversationId={}, folderId={}, videoBvid={}",
                request.conversationId(),
                request.folderId(),
                request.videoBvid()
            ))
            .doOnCancel(() -> log.info("agent stream cancelled: conversationId={}", request.conversationId()))
            .doOnComplete(() -> log.info("agent stream completed: conversationId={}", request.conversationId()))
            .onErrorResume(exception -> {
                log.warn("agent stream failed: conversationId={}, reason={}", request.conversationId(), messageOf(exception), exception);
                return Flux.just(sseEventBuilder.error(messageOf(exception)));
            });
    }

    public Flux<ServerSentEvent<Object>> resume(AgentResumeStreamRequest request) {
        return Flux.defer(() -> buildResumeStream(request))
            .subscribeOn(streamScheduler)
            .doOnSubscribe(ignored -> log.info("agent resume stream started: conversationId={}", request.conversationId()))
            .doOnCancel(() -> log.info("agent resume stream cancelled: conversationId={}", request.conversationId()))
            .doOnComplete(() -> log.info("agent resume stream completed: conversationId={}", request.conversationId()))
            .onErrorResume(exception -> {
                log.warn("agent resume stream failed: conversationId={}, reason={}", request.conversationId(), messageOf(exception), exception);
                return Flux.just(sseEventBuilder.error(messageOf(exception)));
            });
    }

    private Flux<ServerSentEvent<Object>> buildStream(AgentStreamRequest request) {
        boolean created = !StringUtils.hasText(request.conversationId());
        ChatConversationVO conversation = conversationService.ensureConversation(
            request.conversationId(),
            buildTitleHint(request.message()),
            StringUtils.hasText(request.conversationType()) ? request.conversationType() : "AGENT",
            request.folderId(),
            request.videoBvid(),
            request.scopeMode()
        );
        conversationService.appendMessage(conversation.id(), "USER", request.message(), "[]");

        String startedMessage = "统一 Agent 正在分析技能、工具与知识范围";
        Flux<ServerSentEvent<Object>> initialEvents = Flux.just(
            sseEventBuilder.conversation(created, conversation),
            sseEventBuilder.status("started", startedMessage)
        );

        if (isScopeVideoListQuestion(request.message())) {
            log.info(
                "agent stream using fixed scope video list path: conversationId={}, folderId={}, videoBvid={}, message={}",
                conversation.id(),
                conversation.folderId(),
                conversation.videoBvid(),
                request.message()
            );
            return Flux.concat(
                initialEvents,
                Flux.defer(() -> emitResult(
                    conversation,
                    unifiedAgentService.listScopeVideos(conversation.folderId(), conversation.videoBvid()),
                    new LiveStreamState(),
                    true,
                    false
                ))
            );
        }

        UnifiedAgentService.AgentStreamRuntime runtime = unifiedAgentService.createStreamRuntime(
            conversation.id(),
            conversation.folderId(),
            conversation.videoBvid()
        );
        if (runtime == null) {
            return Flux.concat(
                initialEvents,
                Flux.defer(() -> {
                    AgentExecutionResult result = unifiedAgentService.execute(
                        conversation.id(),
                        conversation.folderId(),
                        conversation.videoBvid(),
                        request.message()
                    );
                    return emitResult(conversation, result, new LiveStreamState(), true, false);
                })
            );
        }

        LiveStreamState state = new LiveStreamState();
        Flux<ServerSentEvent<Object>> liveEvents;
        try {
            liveEvents = runtime.agent().stream(request.message(), runtime.config())
                .doOnNext(state.lastOutputRef::set)
                .concatMap(output -> emitLiveNodeOutput(runtime, output, state));
        } catch (GraphRunnerException exception) {
            throw new IllegalStateException("统一 Agent 流式执行失败：" + exception.getMessage(), exception);
        }

        return Flux.concat(
            initialEvents,
            Flux.just(sseEventBuilder.skills(runtime.activeSkills())),
            liveEvents,
            Flux.defer(() -> finalizeRuntime(conversation, runtime, state))
        );
    }

    private Flux<ServerSentEvent<Object>> buildResumeStream(AgentResumeStreamRequest request) {
        ChatConversationVO currentConversation = conversationService.getConversation(request.conversationId());
        ChatConversationVO conversation = conversationService.ensureConversation(
            request.conversationId(),
            currentConversation.title(),
            currentConversation.conversationType(),
            request.folderId(),
            request.videoBvid(),
            request.scopeMode()
        );
        String resumingMessage = "正在恢复统一 Agent 执行";
        return Flux.concat(
            Flux.just(
                sseEventBuilder.conversation(false, conversation),
                sseEventBuilder.status("resuming", resumingMessage)
            ),
            Flux.defer(() -> {
                AgentExecutionResult result = agentResumeService.resume(
                    conversation.id(),
                    conversation.folderId(),
                    conversation.videoBvid(),
                    request
                );
                return emitResult(conversation, result, new LiveStreamState(), true, true);
            })
        );
    }

    private Flux<ServerSentEvent<Object>> finalizeRuntime(
        ChatConversationVO conversation,
        UnifiedAgentService.AgentStreamRuntime runtime,
        LiveStreamState state
    ) {
        NodeOutput lastOutput = state.lastOutputRef.get();
        if (lastOutput == null) {
            throw new IllegalStateException("统一 Agent 流式执行没有返回任何输出。");
        }
        AgentExecutionResult result = unifiedAgentService.adaptStreamResult(
            conversation.id(),
            lastOutput,
            runtime.bridge()
        );
        return emitResult(conversation, result, state, false, false);
    }

    private Flux<ServerSentEvent<Object>> emitResult(
        ChatConversationVO conversation,
        AgentExecutionResult result,
        LiveStreamState state,
        boolean emitSkills,
        boolean resumePendingAssistant
    ) {
        List<ServerSentEvent<Object>> events = new ArrayList<>();
        if (emitSkills) {
            events.add(sseEventBuilder.skills(result.activeSkills()));
        }
        appendPendingEvents(events, "skill", result.skillEvents(), state.sentSkillEvents);
        appendPendingEvents(events, "tool", result.toolEvents(), state.sentToolEvents);
        events.add(sseEventBuilder.reasoning(result.reasoning()));
        events.add(sseEventBuilder.route(result.route()));
        events.add(sseEventBuilder.mode(result.mode()));
        events.add(sseEventBuilder.sources(result.sources()));

        if (result.waitingApproval()) {
            String waitingMessage = "工具调用等待人工确认";
            boolean publishApproval = result.approval() != null
                && !result.approval().items().isEmpty()
                && "publish_to_vault_fs".equals(result.approval().items().getFirst().toolName());
            String approvalPreview = publishApproval
                ? (state.hasStreamedAnswer() ? state.streamedAnswer.toString() : "")
                : (state.hasStreamedAnswer() ? state.streamedAnswer.toString() : waitingMessage);
            ChatMessageVO assistantMessage = toMessageVO(
                conversationService.upsertPendingApprovalAssistantMessage(
                    conversation.id(),
                    approvalPreview,
                    result.sources(),
                    result.citationSegments(),
                    result.mode(),
                    result.route(),
                    result.reasoning(),
                    waitingMessage,
                    result.skillEvents(),
                    result.toolEvents(),
                    result.activeSkills(),
                    result.approval()
                )
            );
            log.info("agent stream waiting approval: conversationId={}", conversation.id());
            events.add(sseEventBuilder.answerNormalized(conversation, result, assistantMessage));
            events.add(sseEventBuilder.status("waiting_approval", waitingMessage));
            events.add(sseEventBuilder.approval(result.approval()));
            return Flux.fromIterable(events);
        }

        appendRemainingAnswerChunks(events, state, result.answer());

        ChatMessageVO assistantMessage = toMessageVO(
            resumePendingAssistant
                ? conversationService.finalizePendingApprovalAssistantMessage(
                    conversation.id(),
                    result.answer(),
                    result.sources(),
                    result.citationSegments(),
                    result.mode(),
                    result.route(),
                    result.reasoning(),
                    result.skillEvents(),
                    result.toolEvents(),
                    result.activeSkills()
                )
                : conversationService.appendAssistantMessage(
                    conversation.id(),
                    result.answer(),
                    result.sources(),
                    result.citationSegments(),
                    result.mode(),
                    result.route(),
                    result.reasoning(),
                    "",
                    result.skillEvents(),
                    result.toolEvents(),
                    result.activeSkills(),
                    null
                )
        );
        events.add(sseEventBuilder.answerNormalized(conversation, result, assistantMessage));
        events.add(sseEventBuilder.done(conversation.id()));
        return Flux.fromIterable(events);
    }

    private Flux<ServerSentEvent<Object>> emitLiveNodeOutput(
        UnifiedAgentService.AgentStreamRuntime runtime,
        NodeOutput output,
        LiveStreamState state
    ) {
        List<ServerSentEvent<Object>> events = new ArrayList<>();
        appendPendingEvents(events, "skill", runtime.bridge().skillEvents(), state.sentSkillEvents);
        appendPendingEvents(events, "tool", runtime.bridge().toolEvents(), state.sentToolEvents);
        if (!(output instanceof StreamingOutput<?> streamingOutput)) {
            return Flux.fromIterable(events);
        }
        if (streamingOutput.getOutputType() != OutputType.AGENT_MODEL_STREAMING) {
            return Flux.fromIterable(events);
        }
        String delta = streamingOutput.chunk();
        if (delta == null || delta.isBlank()) {
            return Flux.fromIterable(events);
        }
        if (runtime.bridge().hasScopeVideoListing()) {
            return Flux.fromIterable(events);
        }
        state.streamedAnswer.append(delta);
        events.add(sseEventBuilder.answer(delta));
        return Flux.fromIterable(events);
    }

    private void appendPendingEvents(
        List<ServerSentEvent<Object>> sink,
        String eventName,
        List<?> items,
        AtomicInteger sentCount
    ) {
        while (sentCount.get() < items.size()) {
            sink.add(sseEventBuilder.event(eventName, items.get(sentCount.getAndIncrement())));
        }
    }

    private List<String> splitChunks(String answer) {
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }
        int chunkSize = 28;
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        for (int start = 0; start < answer.length(); start += chunkSize) {
            chunks.add(answer.substring(start, Math.min(start + chunkSize, answer.length())));
        }
        return chunks.isEmpty() ? List.of(answer) : chunks;
    }

    private void appendRemainingAnswerChunks(
        List<ServerSentEvent<Object>> events,
        LiveStreamState state,
        String finalAnswer
    ) {
        if (!StringUtils.hasText(finalAnswer)) {
            return;
        }
        String streamedAnswer = state.streamedAnswer.toString();
        int sharedPrefixLength = 0;
        int maxPrefixLength = Math.min(streamedAnswer.length(), finalAnswer.length());
        while (sharedPrefixLength < maxPrefixLength
            && streamedAnswer.charAt(sharedPrefixLength) == finalAnswer.charAt(sharedPrefixLength)) {
            sharedPrefixLength += 1;
        }
        if (state.hasStreamedAnswer() && sharedPrefixLength < streamedAnswer.length()) {
            return;
        }
        String remaining = finalAnswer.substring(sharedPrefixLength);
        for (String chunk : splitChunks(remaining)) {
            events.add(sseEventBuilder.answer(chunk));
        }
        state.streamedAnswer.append(remaining);
    }

    private String buildTitleHint(String message) {
        String normalized = message == null ? "" : message.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "Agent 对话";
        }
        return normalized.length() <= 24 ? normalized : normalized.substring(0, 24);
    }

    private boolean isScopeVideoListQuestion(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.trim().replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
        boolean mentionsVideo = normalized.contains("视频");
        boolean asksForScopeListing = normalized.contains("什么视频")
            || normalized.contains("哪些视频")
            || normalized.contains("视频清单")
            || normalized.contains("视频列表")
            || normalized.contains("看到什么视频")
            || normalized.contains("可见视频")
            || normalized.contains("可用视频")
            || normalized.contains("范围内视频")
            || normalized.contains("当前范围")
            || normalized.contains("多少视频")
            || normalized.contains("几个视频")
            || normalized.contains("多少个视频");
        return mentionsVideo && asksForScopeListing;
    }

    private ChatMessageVO toMessageVO(com.bin.bilibrain.model.entity.ChatMessage message) {
        return conversationService.toMessageVO(message);
    }

    private String messageOf(Throwable exception) {
        return exception == null || exception.getMessage() == null
            ? "统一 Agent 流式执行失败。"
            : exception.getMessage();
    }

    private static final class LiveStreamState {
        private final AtomicReference<NodeOutput> lastOutputRef = new AtomicReference<>();
        private final AtomicInteger sentSkillEvents = new AtomicInteger(0);
        private final AtomicInteger sentToolEvents = new AtomicInteger(0);
        private final StringBuilder streamedAnswer = new StringBuilder();

        private boolean hasStreamedAnswer() {
            return streamedAnswer.length() > 0;
        }
    }
}
