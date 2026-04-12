package com.bin.bilibrain.service.skills;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.model.dto.skills.SkillCreateRequest;
import com.bin.bilibrain.model.vo.skills.SkillDetailVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SkillRegistryService {
    private static final Pattern SKILL_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    private final SkillRegistry skillRegistry;
    private final SkillActivationService skillActivationService;
    private final AppProperties appProperties;

    public List<SkillListItemVO> listSkills() {
        skillRegistry.reload();
        return skillRegistry.listAll().stream()
            .sorted(Comparator.comparing(SkillMetadata::getName, String.CASE_INSENSITIVE_ORDER))
            .map(this::toListItem)
            .toList();
    }

    public SkillDetailVO getSkillDetail(String skillName) {
        SkillMetadata metadata = requireSkill(skillName);
        try {
            return new SkillDetailVO(
                metadata.getName(),
                safe(metadata.getDescription()),
                safe(metadata.getSkillPath()),
                skillRegistry.readSkillContent(metadata.getName()),
                skillActivationService.isActive(metadata.getName())
            );
        } catch (IOException exception) {
            throw new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "读取 skill 内容失败。",
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public SkillDetailVO createSkill(SkillCreateRequest request) {
        String skillName = normalizeSkillName(request.name());
        Path skillsRoot = appProperties.getStorage().getSkillsRoot().toAbsolutePath().normalize();
        Path skillDirectory = skillsRoot.resolve(skillName).normalize();
        if (!skillDirectory.startsWith(skillsRoot)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的 skill 路径。", HttpStatus.BAD_REQUEST);
        }
        Path skillFile = skillDirectory.resolve("SKILL.md");
        if (Files.exists(skillFile)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "skill 已存在。", HttpStatus.CONFLICT);
        }

        try {
            Files.createDirectories(skillDirectory);
            Files.writeString(skillFile, renderSkillMarkdown(skillName, request.description(), request.content()));
            skillRegistry.reload();
            return getSkillDetail(skillName);
        } catch (IOException exception) {
            throw new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "创建 skill 文件失败。",
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public void activateSkill(String skillName) {
        skillActivationService.activateSkill(requireSkill(skillName).getName());
    }

    public void deactivateSkill(String skillName) {
        skillActivationService.deactivateSkill(requireSkill(skillName).getName());
    }

    private SkillMetadata requireSkill(String skillName) {
        skillRegistry.reload();
        if (!StringUtils.hasText(skillName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "skill 名称不能为空", HttpStatus.BAD_REQUEST);
        }
        return skillRegistry.get(skillName.trim())
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND_ERROR,
                "找不到这个 skill。",
                HttpStatus.NOT_FOUND
            ));
    }

    private SkillListItemVO toListItem(SkillMetadata metadata) {
        return new SkillListItemVO(
            metadata.getName(),
            safe(metadata.getDescription()),
            safe(metadata.getSkillPath()),
            skillActivationService.isActive(metadata.getName())
        );
    }

    private String normalizeSkillName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "skill 名称不能为空", HttpStatus.BAD_REQUEST);
        }
        String normalizedName = rawName.trim().toLowerCase();
        if (!SKILL_NAME_PATTERN.matcher(normalizedName).matches()) {
            throw new BusinessException(
                ErrorCode.PARAMS_ERROR,
                "skill 名称只允许小写字母、数字和短横线，且必须以字母或数字开头。",
                HttpStatus.BAD_REQUEST
            );
        }
        return normalizedName;
    }

    private String renderSkillMarkdown(String name, String description, String content) {
        return """
            ---
            name: %s
            description: %s
            ---

            # %s

            %s
            """.formatted(
            name,
            sanitizeLine(description),
            name,
            content.trim()
        );
    }

    private String sanitizeLine(String value) {
        return safe(value).replace("\r", " ").replace("\n", " ").trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
