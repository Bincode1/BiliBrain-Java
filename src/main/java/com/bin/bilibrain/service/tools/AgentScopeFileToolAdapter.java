package com.bin.bilibrain.service.tools;

import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.file.ReadFileTool;
import io.agentscope.core.tool.file.WriteFileTool;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentScopeFileToolAdapter {
    private final WorkspaceService workspaceService;
    private final WorkspacePathResolver workspacePathResolver;

    public AgentScopeFileToolAdapter(
        WorkspaceService workspaceService,
        WorkspacePathResolver workspacePathResolver
    ) {
        this.workspaceService = workspaceService;
        this.workspacePathResolver = workspacePathResolver;
    }

    public Map<String, Object> listDirectory(Long workspaceId, String path) {
        Path workspaceRoot = workspaceService.requireWorkspaceRoot(workspaceId);
        Path targetPath = workspacePathResolver.resolve(workspaceRoot, path);
        if (!Files.exists(targetPath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "目录不存在。", HttpStatus.NOT_FOUND);
        }
        if (!Files.isDirectory(targetPath)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标路径不是目录。", HttpStatus.BAD_REQUEST);
        }
        String normalizedPath = workspacePathResolver.normalizeRelative(workspaceRoot, path);
        String text = executeReadTool(workspaceRoot).listDirectory(normalizedPath).blockOptional()
            .map(this::extractText)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "目录读取没有返回结果。",
                HttpStatus.INTERNAL_SERVER_ERROR
            ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", normalizedPath);
        result.put("text", text);
        return result;
    }

    public Map<String, Object> viewTextFile(Long workspaceId, String path, String range) {
        Path workspaceRoot = workspaceService.requireWorkspaceRoot(workspaceId);
        Path targetPath = workspacePathResolver.resolve(workspaceRoot, path);
        if (!Files.exists(targetPath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在。", HttpStatus.NOT_FOUND);
        }
        if (!Files.isRegularFile(targetPath)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标路径不是文件。", HttpStatus.BAD_REQUEST);
        }
        String normalizedPath = workspacePathResolver.normalizeRelative(workspaceRoot, path);
        String text = executeReadTool(workspaceRoot).viewTextFile(normalizedPath, range).blockOptional()
            .map(this::extractText)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "文件读取没有返回结果。",
                HttpStatus.INTERNAL_SERVER_ERROR
            ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", normalizedPath);
        result.put("range", range == null ? "" : range);
        result.put("text", text);
        return result;
    }

    public Map<String, Object> writeTextFile(Long workspaceId, String path, String content, boolean overwrite) {
        Path workspaceRoot = workspaceService.requireWorkspaceRoot(workspaceId);
        Path targetPath = workspacePathResolver.resolve(workspaceRoot, path);
        boolean existedBefore = Files.exists(targetPath);
        if (Files.exists(targetPath) && !overwrite) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "目标文件已存在，如需覆盖请显式设置 overwrite=true。", HttpStatus.CONFLICT);
        }
        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建父目录失败。", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String normalizedPath = workspacePathResolver.normalizeRelative(workspaceRoot, path);
        String text = executeWriteTool(workspaceRoot).writeTextFile(normalizedPath, content, null).blockOptional()
            .map(this::extractText)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "文件写入没有返回结果。",
                HttpStatus.INTERNAL_SERVER_ERROR
            ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", normalizedPath);
        result.put("text", text);
        result.put("created", !existedBefore);
        return result;
    }

    public Map<String, Object> insertTextFile(Long workspaceId, String path, String content, Integer line) {
        if (line == null || line < 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "line 必须是大于 0 的整数。", HttpStatus.BAD_REQUEST);
        }
        Path workspaceRoot = workspaceService.requireWorkspaceRoot(workspaceId);
        Path targetPath = workspacePathResolver.resolve(workspaceRoot, path);
        if (!Files.exists(targetPath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在，暂时不能执行插入。", HttpStatus.NOT_FOUND);
        }
        if (!Files.isRegularFile(targetPath)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标路径不是文件。", HttpStatus.BAD_REQUEST);
        }
        String normalizedPath = workspacePathResolver.normalizeRelative(workspaceRoot, path);
        String text = executeWriteTool(workspaceRoot).insertTextFile(normalizedPath, content, line).blockOptional()
            .map(this::extractText)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "文件插入没有返回结果。",
                HttpStatus.INTERNAL_SERVER_ERROR
            ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", normalizedPath);
        result.put("line", line);
        result.put("text", text);
        return result;
    }

    private ReadFileTool executeReadTool(Path workspaceRoot) {
        return new ReadFileTool(workspaceRoot.toString());
    }

    private WriteFileTool executeWriteTool(Path workspaceRoot) {
        return new WriteFileTool(workspaceRoot.toString());
    }

    private String extractText(ToolResultBlock toolResultBlock) {
        StringBuilder builder = new StringBuilder();
        for (ContentBlock contentBlock : toolResultBlock.getOutput()) {
            if (contentBlock instanceof TextBlock textBlock) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(textBlock.getText());
            } else {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(String.valueOf(contentBlock));
            }
        }
        return builder.toString();
    }
}
