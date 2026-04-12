package com.bin.bilibrain.service.tools;

import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.model.dto.tools.ToolCallRequest;
import com.bin.bilibrain.model.vo.tools.ToolDefinitionVO;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class ToolPolicyService {
    private final Map<String, ToolPolicy> policies = Map.of(
        ToolService.TOOL_READ_SKILL,
        new ToolPolicy(
            ToolService.TOOL_READ_SKILL,
            "读取指定 skill 的描述与完整正文，用于 agent 渐进加载技能。",
            false,
            false,
            true
        ),
        ToolService.TOOL_LIST_WORKSPACES,
        new ToolPolicy(
            ToolService.TOOL_LIST_WORKSPACES,
            "读取当前已注册的工作区列表，为后续工具执行选择 workspace。",
            false,
            false,
            true
        )
    );

    public List<ToolDefinitionVO> listTools() {
        return policies.values().stream()
            .map(policy -> new ToolDefinitionVO(
                policy.getName(),
                policy.getDescription(),
                policy.isApprovalRequired(),
                policy.isEnabled()
            ))
            .sorted(java.util.Comparator.comparing(ToolDefinitionVO::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public ToolPolicy requirePolicy(String toolName) {
        String normalizedToolName = normalizeToolName(toolName);
        ToolPolicy policy = policies.get(normalizedToolName);
        if (policy == null || !policy.isEnabled()) {
            throw new BusinessException(
                ErrorCode.PARAMS_ERROR,
                "暂不支持这个工具调用。",
                HttpStatus.BAD_REQUEST
            );
        }
        return policy;
    }

    public void validateCall(ToolCallRequest request) {
        ToolPolicy policy = requirePolicy(request.toolName());
        if (policy.isWorkspaceRequired() && request.workspaceId() == null) {
            throw new BusinessException(
                ErrorCode.PARAMS_ERROR,
                "当前工具调用必须指定 workspace_id。",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    public boolean requiresApproval(String toolName) {
        return requirePolicy(toolName).isApprovalRequired();
    }

    public String descriptionOf(String toolName) {
        return requirePolicy(toolName).getDescription();
    }

    public String normalizeToolName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "tool_name 不能为空", HttpStatus.BAD_REQUEST);
        }
        return toolName.trim().toLowerCase();
    }

    @Getter
    public static class ToolPolicy {
        private final String name;
        private final String description;
        private final boolean approvalRequired;
        private final boolean workspaceRequired;
        private final boolean enabled;

        public ToolPolicy(
            String name,
            String description,
            boolean approvalRequired,
            boolean workspaceRequired,
            boolean enabled
        ) {
            this.name = name;
            this.description = description;
            this.approvalRequired = approvalRequired;
            this.workspaceRequired = workspaceRequired;
            this.enabled = enabled;
        }
    }
}
