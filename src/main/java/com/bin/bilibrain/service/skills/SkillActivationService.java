package com.bin.bilibrain.service.skills;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.mapper.SkillActivationMapper;
import com.bin.bilibrain.model.entity.SkillActivation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkillActivationService {
    private final SkillActivationMapper skillActivationMapper;
    private final SkillRegistry skillRegistry;

    public Set<String> listActiveSkillNames() {
        return skillActivationMapper.selectList(
                new LambdaQueryWrapper<SkillActivation>()
                    .eq(SkillActivation::getIsActive, true)
            ).stream()
            .map(SkillActivation::getSkillName)
            .collect(Collectors.toSet());
    }

    public boolean isActive(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return false;
        }
        SkillActivation activation = skillActivationMapper.selectById(skillName.trim());
        return activation != null && Boolean.TRUE.equals(activation.getIsActive());
    }

    @Transactional(rollbackFor = Exception.class)
    public void activateSkill(String skillName) {
        upsert(skillName, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deactivateSkill(String skillName) {
        upsert(skillName, false);
    }

    private void upsert(String skillName, boolean active) {
        String normalizedName = requireKnownSkill(skillName);
        LocalDateTime now = LocalDateTime.now();
        SkillActivation existing = skillActivationMapper.selectById(normalizedName);
        if (existing == null) {
            skillActivationMapper.insert(SkillActivation.builder()
                .skillName(normalizedName)
                .isActive(active)
                .createdAt(now)
                .updatedAt(now)
                .build());
            return;
        }
        existing.setIsActive(active);
        existing.setUpdatedAt(now);
        skillActivationMapper.updateById(existing);
    }

    private String requireKnownSkill(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "skill 名称不能为空", HttpStatus.BAD_REQUEST);
        }
        String normalizedName = skillName.trim();
        if (!skillRegistry.contains(normalizedName)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "找不到这个 skill。", HttpStatus.NOT_FOUND);
        }
        return normalizedName;
    }
}
