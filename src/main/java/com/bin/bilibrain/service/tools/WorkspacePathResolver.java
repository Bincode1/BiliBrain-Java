package com.bin.bilibrain.service.tools;

import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

@Service
public class WorkspacePathResolver {

    public Path resolve(Path workspaceRoot, String rawPath) {
        String candidate = StringUtils.hasText(rawPath) ? rawPath.trim() : ".";
        Path relativePath = Path.of(candidate).normalize();
        if (relativePath.isAbsolute()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "path 只允许 workspace 内相对路径。", HttpStatus.BAD_REQUEST);
        }
        Path resolvedPath = workspaceRoot.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(workspaceRoot)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的 workspace 路径。", HttpStatus.BAD_REQUEST);
        }
        return resolvedPath;
    }

    public String normalizeRelative(Path workspaceRoot, String rawPath) {
        Path resolvedPath = resolve(workspaceRoot, rawPath);
        Path relativePath = workspaceRoot.relativize(resolvedPath);
        String normalized = relativePath.toString().replace('\\', '/');
        return normalized.isBlank() ? "." : normalized;
    }
}
