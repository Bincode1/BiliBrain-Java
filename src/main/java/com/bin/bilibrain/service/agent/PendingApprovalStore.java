package com.bin.bilibrain.service.agent;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class PendingApprovalStore {
    private final ConcurrentMap<String, InterruptionMetadata> pendingApprovals = new ConcurrentHashMap<>();

    public void put(String conversationId, InterruptionMetadata interruptionMetadata) {
        pendingApprovals.put(conversationId, interruptionMetadata);
    }

    public Optional<InterruptionMetadata> get(String conversationId) {
        return Optional.ofNullable(pendingApprovals.get(conversationId));
    }

    public void remove(String conversationId) {
        pendingApprovals.remove(conversationId);
    }
}
