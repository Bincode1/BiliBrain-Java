package com.bin.bilibrain.tools;

import com.bin.bilibrain.controller.ToolController;
import com.bin.bilibrain.exception.GlobalExceptionHandler;
import com.bin.bilibrain.model.vo.tools.ToolWorkspaceVO;
import com.bin.bilibrain.service.tools.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ToolController.class)
@Import(GlobalExceptionHandler.class)
class ToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkspaceService workspaceService;

    @Test
    void listWorkspacesReturnsItems() throws Exception {
        when(workspaceService.listWorkspaces()).thenReturn(List.of(
            new ToolWorkspaceVO(1L, "Java Agent", "java-agent", "D:/learnProjects/bilibrain/data/tool_workspaces/java-agent", "Agent 工作目录", "2026-04-12T12:40:00")
        ));

        mockMvc.perform(get("/api/tools/workspaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].workspace_key").value("java-agent"));
    }

    @Test
    void createWorkspaceReturnsCreatedItem() throws Exception {
        when(workspaceService.createWorkspace(any())).thenReturn(
            new ToolWorkspaceVO(2L, "Research Space", "research-space", "D:/learnProjects/bilibrain/data/tool_workspaces/research-space", "", "2026-04-12T12:41:00")
        );

        mockMvc.perform(post("/api/tools/workspaces")
                .contentType("application/json")
                .content("""
                    {"name":"Research Space","description":""}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(2))
            .andExpect(jsonPath("$.data.workspace_key").value("research-space"));
    }
}
