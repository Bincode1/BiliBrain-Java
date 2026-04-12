package com.bin.bilibrain.tools;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.mapper.ToolWorkspaceMapper;
import com.bin.bilibrain.model.dto.tools.WorkspaceCreateRequest;
import com.bin.bilibrain.model.entity.ToolWorkspace;
import com.bin.bilibrain.model.vo.tools.ToolWorkspaceVO;
import com.bin.bilibrain.service.tools.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createWorkspaceCreatesDirectoryAndPersistsWorkspace() throws Exception {
        ToolWorkspaceMapper toolWorkspaceMapper = mock(ToolWorkspaceMapper.class);
        when(toolWorkspaceMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ToolWorkspace workspace = invocation.getArgument(0);
            workspace.setId(1L);
            return 1;
        }).when(toolWorkspaceMapper).insert(any(ToolWorkspace.class));

        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setToolsWorkspaceRoot(tempDir.resolve("tool-workspaces"));

        WorkspaceService workspaceService = new WorkspaceService(toolWorkspaceMapper, appProperties);

        ToolWorkspaceVO result = workspaceService.createWorkspace(new WorkspaceCreateRequest("Java Agent", "Agent 工作目录"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.workspaceKey()).isEqualTo("java-agent");
        assertThat(Files.isDirectory(Path.of(result.workspacePath()))).isTrue();
        assertThat(result.description()).isEqualTo("Agent 工作目录");
    }
}
