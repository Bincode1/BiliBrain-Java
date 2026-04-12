package com.bin.bilibrain.service.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.mapper.ToolWorkspaceMapper;
import com.bin.bilibrain.model.dto.tools.WorkspaceCreateRequest;
import com.bin.bilibrain.model.entity.ToolWorkspace;
import com.bin.bilibrain.model.vo.tools.ToolWorkspaceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final ToolWorkspaceMapper toolWorkspaceMapper;
    private final AppProperties appProperties;

    public java.util.List<ToolWorkspaceVO> listWorkspaces() {
        return toolWorkspaceMapper.selectList(
                new LambdaQueryWrapper<ToolWorkspace>()
                    .orderByDesc(ToolWorkspace::getUpdatedAt)
            ).stream()
            .map(this::toVO)
            .toList();
    }

    public ToolWorkspaceVO createWorkspace(WorkspaceCreateRequest request) {
        String name = normalizeName(request.name());
        String workspaceKey = allocateWorkspaceKey(name);
        Path root = appProperties.getStorage().getToolsWorkspaceRoot().toAbsolutePath().normalize();
        Path workspacePath = root.resolve(workspaceKey).normalize();
        if (!workspacePath.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的 workspace 路径。", HttpStatus.BAD_REQUEST);
        }

        try {
            Files.createDirectories(root);
            Files.createDirectories(workspacePath);
        } catch (IOException exception) {
            throw new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "创建 workspace 目录失败。",
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        LocalDateTime now = LocalDateTime.now();
        ToolWorkspace workspace = ToolWorkspace.builder()
            .name(name)
            .workspaceKey(workspaceKey)
            .workspacePath(workspacePath.toString())
            .description(blankToEmpty(request.description()))
            .createdAt(now)
            .updatedAt(now)
            .build();
        toolWorkspaceMapper.insert(workspace);
        return toVO(workspace);
    }

    private String allocateWorkspaceKey(String name) {
        String baseKey = slugify(name);
        String candidate = baseKey;
        int suffix = 2;
        while (toolWorkspaceMapper.selectOne(
            new LambdaQueryWrapper<ToolWorkspace>().eq(ToolWorkspace::getWorkspaceKey, candidate)
        ) != null) {
            candidate = baseKey + "-" + suffix++;
        }
        return candidate;
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "workspace 名称不能为空", HttpStatus.BAD_REQUEST);
        }
        return name.trim().replaceAll("\\s+", " ");
    }

    private String slugify(String value) {
        String slug = value.trim().toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
        if (slug.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "workspace 名称必须包含字母或数字", HttpStatus.BAD_REQUEST);
        }
        return slug;
    }

    private ToolWorkspaceVO toVO(ToolWorkspace workspace) {
        return new ToolWorkspaceVO(
            workspace.getId(),
            workspace.getName(),
            workspace.getWorkspaceKey(),
            workspace.getWorkspacePath(),
            blankToEmpty(workspace.getDescription()),
            workspace.getUpdatedAt() == null ? "" : workspace.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    private String blankToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
