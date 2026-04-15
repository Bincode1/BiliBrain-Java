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
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ToolService {
    public static final String TOOL_READ_SKILL = "read_skill";
    public static final String TOOL_LIST_WORKSPACES = "list_workspaces";
    public static final String TOOL_LIST_DIRECTORY = "list_directory";
    public static final String TOOL_VIEW_TEXT_FILE = "view_text_file";
    public static final String TOOL_WRITE_TEXT_FILE = "write_text_file";
    public static final String TOOL_INSERT_TEXT_FILE = "insert_text_file";
    public static final String TOOL_PUBLISH_TO_VAULT_FS = "publish_to_vault_fs";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final ToolCallMapper toolCallMapper;
    private final ToolPolicyService toolPolicyService;
    private final SkillRegistryService skillRegistryService;
    private final WorkspaceService workspaceService;
    private final AgentScopeFileToolAdapter agentScopeFileToolAdapter;
    private final VaultPublishingService vaultPublishingService;
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
            case TOOL_LIST_DIRECTORY -> agentScopeFileToolAdapter.listDirectory(
                request.workspaceId(),
                stringArg(request, "path", ".")
            );
            case TOOL_VIEW_TEXT_FILE -> agentScopeFileToolAdapter.viewTextFile(
                request.workspaceId(),
                requiredStringArg(request, "path"),
                stringArg(request, "range", null)
            );
            case TOOL_WRITE_TEXT_FILE -> agentScopeFileToolAdapter.writeTextFile(
                request.workspaceId(),
                requiredStringArg(request, "path"),
                requiredStringArg(request, "content"),
                booleanArg(request, "overwrite", false)
            );
            case TOOL_INSERT_TEXT_FILE -> agentScopeFileToolAdapter.insertTextFile(
                request.workspaceId(),
                requiredStringArg(request, "path"),
                requiredStringArg(request, "content"),
                requiredIntArg(request, "line")
            );
            case TOOL_PUBLISH_TO_VAULT_FS -> vaultPublishingService.publishToVaultFs(
                requiredStringArg(request, "kind"),
                requiredStringArg(request, "title"),
                requiredStringArg(request, "content_markdown"),
                requiredStringArg(request, "scope_type"),
                requiredStringArg(request, "scope_id"),
                listArg(request, "source_refs")
            );
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

    private String requiredStringArg(ToolCallRequest request, String key) {
        String value = stringArg(request, key, null);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, key + " 不能为空", HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private String stringArg(ToolCallRequest request, String key, String defaultValue) {
        Object value = arguments(request).get(key);
        if (value == null) {
            return defaultValue;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? defaultValue : normalized;
    }

    private boolean booleanArg(ToolCallRequest request, String key, boolean defaultValue) {
        Object value = arguments(request).get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Integer requiredIntArg(ToolCallRequest request, String key) {
        Object value = arguments(request).get(key);
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, key + " 不能为空", HttpStatus.BAD_REQUEST);
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, key + " 必须是整数", HttpStatus.BAD_REQUEST);
        }
    }

    private List<?> listArg(ToolCallRequest request, String key) {
        Object value = arguments(request).get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        return Collections.singletonList(value);
    }

    private Map<String, Object> arguments(ToolCallRequest request) {
        return request.arguments() == null ? Map.of() : request.arguments();
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
