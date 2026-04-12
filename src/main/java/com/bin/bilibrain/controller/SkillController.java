package com.bin.bilibrain.controller;

import com.bin.bilibrain.common.BaseResponse;
import com.bin.bilibrain.common.ResultUtils;
import com.bin.bilibrain.model.dto.skills.SkillCreateRequest;
import com.bin.bilibrain.model.dto.skills.SkillToggleRequest;
import com.bin.bilibrain.model.vo.skills.SkillDetailVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import com.bin.bilibrain.service.skills.SkillRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {
    private final SkillRegistryService skillRegistryService;

    @GetMapping
    public BaseResponse<List<SkillListItemVO>> listSkills() {
        return ResultUtils.success(skillRegistryService.listSkills());
    }

    @GetMapping("/{name}")
    public BaseResponse<SkillDetailVO> getSkill(@PathVariable String name) {
        return ResultUtils.success(skillRegistryService.getSkillDetail(name));
    }

    @PostMapping("/create")
    public BaseResponse<SkillDetailVO> createSkill(@Valid @RequestBody SkillCreateRequest request) {
        return ResultUtils.success(skillRegistryService.createSkill(request));
    }

    @PostMapping("/activate")
    public BaseResponse<Boolean> activateSkill(@Valid @RequestBody SkillToggleRequest request) {
        skillRegistryService.activateSkill(request.name());
        return ResultUtils.success(true);
    }

    @PostMapping("/deactivate")
    public BaseResponse<Boolean> deactivateSkill(@Valid @RequestBody SkillToggleRequest request) {
        skillRegistryService.deactivateSkill(request.name());
        return ResultUtils.success(true);
    }
}
