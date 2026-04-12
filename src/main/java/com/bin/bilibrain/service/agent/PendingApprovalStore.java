package com.bin.bilibrain.service.agent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.std.SpringAIStateSerializer;
import com.bin.bilibrain.service.system.AppStateService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class PendingApprovalStore {
    private static final String STATE_KEY_PREFIX = "agent:approval:";
    private static final TypeReference<PendingApprovalSnapshot> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    private final AppStateService appStateService;
    private final StateSerializer stateSerializer = new SpringAIStateSerializer();
    private final ConcurrentMap<String, InterruptionMetadata> pendingApprovals = new ConcurrentHashMap<>();

    public void put(String conversationId, InterruptionMetadata interruptionMetadata) {
        pendingApprovals.put(conversationId, interruptionMetadata);
        appStateService.saveJson(
            stateKey(conversationId),
            PendingApprovalSnapshot.from(interruptionMetadata, encodeState(interruptionMetadata))
        );
    }

    public Optional<InterruptionMetadata> get(String conversationId) {
        InterruptionMetadata cached = pendingApprovals.get(conversationId);
        if (cached != null) {
            return Optional.of(cached);
        }
        return appStateService.loadJsonOptional(stateKey(conversationId), SNAPSHOT_TYPE)
            .map(this::restore)
            .map(interruption -> {
                pendingApprovals.put(conversationId, interruption);
                return interruption;
            });
    }

    public void remove(String conversationId) {
        pendingApprovals.remove(conversationId);
        appStateService.delete(stateKey(conversationId));
    }

    private String stateKey(String conversationId) {
        return STATE_KEY_PREFIX + conversationId;
    }

    private String encodeState(InterruptionMetadata interruptionMetadata) {
        try {
            return Base64.getEncoder().encodeToString(
                stateSerializer.dataToBytes(interruptionMetadata.state().data())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("序列化待审批的 Agent 状态失败。", exception);
        }
    }

    private InterruptionMetadata restore(PendingApprovalSnapshot snapshot) {
        InterruptionMetadata.Builder builder = InterruptionMetadata.builder()
            .nodeId(snapshot.nodeId())
            .state(decodeState(snapshot.statePayload()));
        snapshot.toolFeedbacks().stream()
            .map(PendingApprovalToolFeedbackSnapshot::toToolFeedback)
            .forEach(builder::addToolFeedback);
        return builder.build();
    }

    private OverAllState decodeState(String statePayload) {
        if (!StringUtils.hasText(statePayload)) {
            return new OverAllState();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(statePayload);
            return stateSerializer.stateOf(stateSerializer.dataFromBytes(bytes));
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("恢复待审批的 Agent 状态失败。", exception);
        }
    }

    private record PendingApprovalSnapshot(
        String nodeId,
        String statePayload,
        List<PendingApprovalToolFeedbackSnapshot> toolFeedbacks
    ) {
        private static PendingApprovalSnapshot from(InterruptionMetadata interruptionMetadata, String statePayload) {
            return new PendingApprovalSnapshot(
                interruptionMetadata.node(),
                statePayload,
                interruptionMetadata.toolFeedbacks().stream()
                    .map(PendingApprovalToolFeedbackSnapshot::from)
                    .toList()
            );
        }
    }

    private record PendingApprovalToolFeedbackSnapshot(
        String id,
        String name,
        String arguments,
        String description,
        String result
    ) {
        private static PendingApprovalToolFeedbackSnapshot from(InterruptionMetadata.ToolFeedback toolFeedback) {
            return new PendingApprovalToolFeedbackSnapshot(
                toolFeedback.getId(),
                toolFeedback.getName(),
                toolFeedback.getArguments(),
                toolFeedback.getDescription(),
                toolFeedback.getResult() == null ? "" : toolFeedback.getResult().name()
            );
        }

        private InterruptionMetadata.ToolFeedback toToolFeedback() {
            InterruptionMetadata.ToolFeedback.Builder builder = InterruptionMetadata.ToolFeedback.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .description(description);
            if (StringUtils.hasText(result)) {
                builder.result(InterruptionMetadata.ToolFeedback.FeedbackResult.valueOf(result));
            }
            return builder.build();
        }
    }
}
