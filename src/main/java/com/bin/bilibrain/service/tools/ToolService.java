package com.bin.bilibrain.service.tools;

import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.mapper.ToolCallMapper;
import com.bin.bilibrain.model.dto.tools.ToolCallRequest;
import com.bin.bilibrain.model.entity.ToolCall;
import com.bin.bilibrain.model.vo.skills.SkillDetailVO;
import com.bin.bilibrain.model.vo.tools.ToolCallResultVO;
import com.bin.bilibrain.model.vo.tools.ToolDefinitionVO;
import com.bin.bilibrain.service.skills.SkillRegistryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ToolService {
    public static final String TOOL_READ_SKILL = "read_skill";
    public static final String TOOL_LIST_WORKSPACES = "list_workspaces";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final ToolCallMapper toolCallMapper;
    private final ToolPolicyService toolPolicyService;
    private final SkillRegistryService skillRegistryService;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public List<ToolDefinitionVO> listTools() {
        return toolPolicyService.listTools();
    }

    public ToolCallResultVO callTool(ToolCallRequest request) {
        toolPolicyService.validateCall(request);
        LocalDateTime now = LocalDateTime.now();
        try {
            Map<String, Object> result = execute(request);
            ToolCall toolCall = persistCall(request, STATUS_SUCCESS, result, now);
            return new ToolCallResultVO(
                toolCall.getId(),
                toolCall.getToolName(),
                toolCall.getStatus(),
                result,
                toolCall.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
        } catch (RuntimeException exception) {
            persistCall(request, STATUS_FAILED, Map.of("error", exception.getMessage()), now);
            throw exception;
        }
    }

    private Map<String, Object> execute(ToolCallRequest request) {
        String toolName = toolPolicyService.normalizeToolName(request.toolName());
        return switch (toolName) {
            case TOOL_READ_SKILL -> readSkill(request.skillName());
            case TOOL_LIST_WORKSPACES -> listWorkspacesResult();
            default -> throw new BusinessException(
                ErrorCode.PARAMS_ERROR,
                "暂不支持这个工具调用。",
                HttpStatus.BAD_REQUEST
            );
        };
    }

    private Map<String, Object> readSkill(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "skill_name 不能为空", HttpStatus.BAD_REQUEST);
        }
        SkillDetailVO detail = skillRegistryService.getSkillDetail(skillName);
        return Map.of(
            "name", detail.name(),
            "description", detail.description(),
            "active", detail.active(),
            "content", detail.content()
        );
    }

    private Map<String, Object> listWorkspacesResult() {
        List<?> workspaces = workspaceService.listWorkspaces();
        return Map.of(
            "count", workspaces.size(),
            "workspaces", workspaces
        );
    }

    private ToolCall persistCall(ToolCallRequest request, String status, Map<String, Object> result, LocalDateTime now) {
        ToolCall toolCall = ToolCall.builder()
            .workspaceId(request.workspaceId())
            .toolName(toolPolicyService.normalizeToolName(request.toolName()))
            .status(status)
            .requestJson(writeJson(request))
            .responseJson(writeJson(result))
            .createdAt(now)
            .updatedAt(now)
            .build();
        toolCallMapper.insert(toolCall);
        return toolCall;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "序列化工具调用内容失败。",
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
