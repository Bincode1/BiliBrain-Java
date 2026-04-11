package com.bin.bilibrain.system;

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

    @GetMapping({"/health", "/system/health"})
    public HealthResponse health() {
        return new HealthResponse("ok");
    }

    @GetMapping("/settings")
    public ProcessingSettingsPayload getSettings() {
        return processingSettingsService.getSettings();
    }

    @PostMapping("/settings")
    public ProcessingSettingsPayload updateSettings(@Valid @RequestBody ProcessingSettingsPayload payload) {
        return processingSettingsService.updateSettings(payload);
    }
}
