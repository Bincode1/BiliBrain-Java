package com.bin.bilibrain.controller;

import com.bin.bilibrain.common.BaseResponse;
import com.bin.bilibrain.common.ResultUtils;
import com.bin.bilibrain.model.dto.tools.ToolCallRequest;
import com.bin.bilibrain.model.dto.tools.WorkspaceCreateRequest;
import com.bin.bilibrain.model.vo.tools.ToolCallResultVO;
import com.bin.bilibrain.model.vo.tools.ToolDefinitionVO;
import com.bin.bilibrain.model.vo.tools.ToolWorkspaceVO;
import com.bin.bilibrain.service.tools.ToolService;
import com.bin.bilibrain.service.tools.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {
    private final ToolService toolService;
    private final WorkspaceService workspaceService;

    @GetMapping
    public BaseResponse<List<ToolDefinitionVO>> listTools() {
        return ResultUtils.success(toolService.listTools());
    }

    @GetMapping("/workspaces")
    public BaseResponse<List<ToolWorkspaceVO>> listWorkspaces() {
        return ResultUtils.success(workspaceService.listWorkspaces());
    }

    @PostMapping("/workspaces")
    public BaseResponse<ToolWorkspaceVO> createWorkspace(@Valid @RequestBody WorkspaceCreateRequest request) {
        return ResultUtils.success(workspaceService.createWorkspace(request));
    }

    @PostMapping("/call")
    public BaseResponse<ToolCallResultVO> callTool(@Valid @RequestBody ToolCallRequest request) {
        return ResultUtils.success(toolService.callTool(request));
    }
}
