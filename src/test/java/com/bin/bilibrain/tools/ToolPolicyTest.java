package com.bin.bilibrain.tools;

import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.model.dto.tools.ToolCallRequest;
import com.bin.bilibrain.service.tools.ToolPolicyService;
import com.bin.bilibrain.service.tools.ToolService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolPolicyTest {

    @Test
    void listToolsExposesApprovalFlags() {
        ToolPolicyService toolPolicyService = new ToolPolicyService();

        var tools = toolPolicyService.listTools();

        assertThat(tools).hasSize(2);
        assertThat(tools)
            .filteredOn(tool -> tool.name().equals(ToolService.TOOL_LIST_WORKSPACES))
            .singleElement()
            .satisfies(tool -> assertThat(tool.approvalRequired()).isTrue());
    }

    @Test
    void validateCallRejectsUnknownTool() {
        ToolPolicyService toolPolicyService = new ToolPolicyService();

        assertThatThrownBy(() -> toolPolicyService.validateCall(new ToolCallRequest("unknown_tool", null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("暂不支持这个工具调用");
    }
}
