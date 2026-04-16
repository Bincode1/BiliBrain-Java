package com.bin.bilibrain.service.tools;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VaultPublishingService {
    private final AppProperties appProperties;

    public VaultPublishingService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public Map<String, Object> publishToVaultFs(
        String kind,
        String title,
        String contentMarkdown,
        String scopeType,
        String scopeId,
        List<?> sourceRefs
    ) {
        String normalizedKind = normalizeRequired(kind, "kind");
        String normalizedTitle = normalizeRequired(title, "title");
        String normalizedContent = normalizeRequired(contentMarkdown, "content_markdown");
        String normalizedScopeType = normalizeRequired(scopeType, "scope_type");
        String normalizedScopeId = normalizeRequired(scopeId, "scope_id");

        Path vaultRoot = appProperties.getPublishing().getVaultRoot().toAbsolutePath().normalize();
        Path targetDirectory = resolveTargetDirectory(vaultRoot, normalizedKind);
        ensureWithinVault(vaultRoot, targetDirectory);

        LocalDateTime publishedAt = LocalDateTime.now();
        Path targetPath = uniqueTargetPath(targetDirectory, slugify(normalizedTitle));
        String markdown = renderMarkdown(
            normalizedTitle,
            normalizedKind,
            normalizedScopeType,
            normalizedScopeId,
            publishedAt,
            sourceRefs == null ? 0 : sourceRefs.size(),
            normalizedContent
        );

        try {
            Files.createDirectories(targetDirectory);
            Files.writeString(targetPath, markdown, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "写入本地文件失败。", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "published");
        result.put("kind", normalizedKind);
        result.put("title", normalizedTitle);
        result.put("file_name", targetPath.getFileName().toString());
        result.put("content_markdown", normalizedContent);
        result.put("scope_type", normalizedScopeType);
        result.put("scope_id", normalizedScopeId);
        result.put("target_path", targetPath.toAbsolutePath().normalize().toString());
        result.put("published_at", publishedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return result;
    }

    public Path previewTargetPath(String kind, String title) {
        String normalizedKind = normalizeRequired(kind, "kind");
        String normalizedTitle = normalizeRequired(title, "title");
        Path vaultRoot = appProperties.getPublishing().getVaultRoot().toAbsolutePath().normalize();
        Path targetDirectory = resolveTargetDirectory(vaultRoot, normalizedKind);
        ensureWithinVault(vaultRoot, targetDirectory);
        return uniqueTargetPath(targetDirectory, slugify(normalizedTitle)).toAbsolutePath().normalize();
    }

    private String renderMarkdown(
        String title,
        String kind,
        String scopeType,
        String scopeId,
        LocalDateTime publishedAt,
        int sourcesCount,
        String contentMarkdown
    ) {
        return """
            ---
            title: "%s"
            kind: "%s"
            scope_type: "%s"
            scope_id: "%s"
            published_at: "%s"
            sources_count: %d
            ---

            %s
            """.formatted(
            escapeYaml(title),
            escapeYaml(kind),
            escapeYaml(scopeType),
            escapeYaml(scopeId),
            publishedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            sourcesCount,
            contentMarkdown.trim()
        );
    }

    private Path uniqueTargetPath(Path targetDirectory, String baseName) {
        String safeBaseName = baseName.isBlank() ? "untitled" : baseName;
        Path candidate = targetDirectory.resolve(safeBaseName + ".md").normalize();
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = targetDirectory.resolve(safeBaseName + "-" + suffix + ".md").normalize();
            suffix += 1;
        }
        return candidate;
    }

    private Path resolveTargetDirectory(Path vaultRoot, String normalizedKind) {
        return switch (normalizedKind) {
            case "video_note" -> vaultRoot.resolve(appProperties.getPublishing().getVideoNotesDir()).normalize();
            case "folder_guide" -> vaultRoot.resolve(appProperties.getPublishing().getFolderGuidesDir()).normalize();
            case "review_plan" -> vaultRoot.resolve(appProperties.getPublishing().getReviewPlansDir()).normalize();
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的文件类型。", HttpStatus.BAD_REQUEST);
        };
    }

    private void ensureWithinVault(Path vaultRoot, Path path) {
        if (!path.startsWith(vaultRoot)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的文件路径。", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeRequired(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, field + " 不能为空", HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private String slugify(String value) {
        return value.trim()
            .toLowerCase()
            .replaceAll("[\\\\/:*?\"<>|]+", "-")
            .replaceAll("\\s+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
    }

    private String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
