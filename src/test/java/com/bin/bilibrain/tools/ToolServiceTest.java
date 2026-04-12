package com.bin.bilibrain.tools;

import com.bin.bilibrain.mapper.ToolCallMapper;
import com.bin.bilibrain.model.dto.tools.ToolCallRequest;
import com.bin.bilibrain.model.entity.ToolCall;
import com.bin.bilibrain.model.vo.skills.SkillDetailVO;
import com.bin.bilibrain.model.vo.tools.ToolCallResultVO;
import com.bin.bilibrain.model.vo.tools.ToolWorkspaceVO;
import com.bin.bilibrain.service.skills.SkillRegistryService;
import com.bin.bilibrain.service.tools.ToolService;
import com.bin.bilibrain.service.tools.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolServiceTest {

    @Test
    void callReadSkillReturnsSkillContentAndAuditsCall() {
        ToolCallMapper toolCallMapper = mock(ToolCallMapper.class);
        SkillRegistryService skillRegistryService = mock(SkillRegistryService.class);
        WorkspaceService workspaceService = mock(WorkspaceService.class);
        when(skillRegistryService.getSkillDetail("java-rag")).thenReturn(
            new SkillDetailVO("java-rag", "负责 Java RAG 问答", "skills/java-rag/SKILL.md", "技能正文", true)
        );
        doAnswer(invocation -> {
            ToolCall toolCall = invocation.getArgument(0);
            toolCall.setId(10L);
            return 1;
        }).when(toolCallMapper).insert(any(ToolCall.class));

        ToolService toolService = new ToolService(toolCallMapper, skillRegistryService, workspaceService, new ObjectMapper());

        ToolCallResultVO result = toolService.callTool(new ToolCallRequest("read_skill", null, "java-rag"));

        assertThat(result.callId()).isEqualTo(10L);
        assertThat(result.toolName()).isEqualTo("read_skill");
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.result()).containsEntry("name", "java-rag");
        verify(toolCallMapper).insert(any(ToolCall.class));
    }

    @Test
    void callListWorkspacesReturnsWorkspaceCatalog() {
        ToolCallMapper toolCallMapper = mock(ToolCallMapper.class);
        SkillRegistryService skillRegistryService = mock(SkillRegistryService.class);
        WorkspaceService workspaceService = mock(WorkspaceService.class);
        when(workspaceService.listWorkspaces()).thenReturn(List.of(
            new ToolWorkspaceVO(1L, "Java Agent", "java-agent", "D:/data/tool_workspaces/java-agent", "", "2026-04-12T13:00:00")
        ));
        doAnswer(invocation -> {
            ToolCall toolCall = invocation.getArgument(0);
            toolCall.setId(11L);
            return 1;
        }).when(toolCallMapper).insert(any(ToolCall.class));

        ToolService toolService = new ToolService(toolCallMapper, skillRegistryService, workspaceService, new ObjectMapper());

        ToolCallResultVO result = toolService.callTool(new ToolCallRequest("list_workspaces", null, null));

        assertThat(result.callId()).isEqualTo(11L);
        assertThat(result.result()).containsEntry("count", 1);
        assertThat(result.result().get("workspaces")).isInstanceOf(List.class);
        assertThat((List<?>) result.result().get("workspaces")).hasSize(1);
    }
}
