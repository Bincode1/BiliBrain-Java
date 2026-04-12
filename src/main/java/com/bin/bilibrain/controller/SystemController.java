package com.bin.bilibrain.controller;

import com.bin.bilibrain.common.BaseResponse;
import com.bin.bilibrain.common.ResultUtils;
import com.bin.bilibrain.model.dto.system.ProcessingSettingsUpdateRequest;
import com.bin.bilibrain.model.vo.system.HealthStatusVO;
import com.bin.bilibrain.model.vo.system.ProcessingSettingsVO;
import com.bin.bilibrain.model.vo.system.SystemReadinessVO;
import com.bin.bilibrain.service.system.ProcessingSettingsService;
import com.bin.bilibrain.service.system.SystemReadinessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemController {
    private final ProcessingSettingsService processingSettingsService;
    private final SystemReadinessService systemReadinessService;

    @GetMapping({"/health", "/system/health"})
    public BaseResponse<HealthStatusVO> health() {
        return ResultUtils.success(new HealthStatusVO("ok"));
    }

    @GetMapping("/system/readiness")
    public BaseResponse<SystemReadinessVO> readiness() {
        return ResultUtils.success(systemReadinessService.getReadiness());
    }

    @GetMapping("/settings")
    public BaseResponse<ProcessingSettingsVO> getSettings() {
        return ResultUtils.success(processingSettingsService.getSettings());
    }

    @PostMapping("/settings")
    public BaseResponse<ProcessingSettingsVO> updateSettings(@Valid @RequestBody ProcessingSettingsUpdateRequest request) {
        return ResultUtils.success(processingSettingsService.updateSettings(request));
    }
}
