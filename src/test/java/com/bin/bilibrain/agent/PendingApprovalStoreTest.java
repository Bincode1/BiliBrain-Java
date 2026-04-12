package com.bin.bilibrain.agent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.bin.bilibrain.service.agent.PendingApprovalStore;
import com.bin.bilibrain.service.system.AppStateService;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PendingApprovalStoreTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private AppStateService appStateService;

    @Test
    void restoresApprovalFromPersistentStateAfterCacheMiss() {
        PendingApprovalStore store = new PendingApprovalStore(appStateService);
        InterruptionMetadata interruption = InterruptionMetadata.builder()
            .nodeId("tool_call")
            .state(new OverAllState(Map.of(
                "input", "列出当前工作区",
                "messages", List.of("hello")
            )))
            .addToolFeedback(InterruptionMetadata.ToolFeedback.builder()
                .id("tool-1")
                .name("list_workspaces")
                .arguments("{}")
                .description("读取工作区列表前需要人工确认。")
                .build())
            .build();

        store.put("conv-approval", interruption);

        PendingApprovalStore restoredStore = new PendingApprovalStore(appStateService);
        InterruptionMetadata restored = restoredStore.get("conv-approval").orElseThrow();

        assertThat(restored.node()).isEqualTo("tool_call");
        assertThat(restored.state().value("input", String.class)).contains("列出当前工作区");
        assertThat(restored.toolFeedbacks()).hasSize(1);
        assertThat(restored.toolFeedbacks().getFirst().getName()).isEqualTo("list_workspaces");
        assertThat(restored.toolFeedbacks().getFirst().getArguments()).isEqualTo("{}");
    }

    @Test
    void removeClearsPersistentApprovalSnapshot() {
        PendingApprovalStore store = new PendingApprovalStore(appStateService);
        InterruptionMetadata interruption = InterruptionMetadata.builder()
            .nodeId("tool_call")
            .state(new OverAllState(Map.of("input", "approve")))
            .addToolFeedback(InterruptionMetadata.ToolFeedback.builder()
                .id("tool-1")
                .name("list_workspaces")
                .arguments("{}")
                .description("读取工作区列表前需要人工确认。")
                .build())
            .build();

        store.put("conv-cleanup", interruption);
        store.remove("conv-cleanup");

        assertThat(store.get("conv-cleanup")).isEmpty();
        assertThat(appStateService.getUpdatedAt("agent:approval:conv-cleanup")).isEmpty();
    }
}
