package com.bin.bilibrain.system;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.system.persistence.ProcessingSettingsEntity;
import com.bin.bilibrain.system.persistence.ProcessingSettingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProcessingSettingsService {
    private static final long SETTINGS_ROW_ID = 1L;

    private final ProcessingSettingsMapper processingSettingsMapper;
    private final AppProperties appProperties;

    public ProcessingSettingsPayload getSettings() {
        ProcessingSettingsEntity entity = ensureSettingsRow();
        return new ProcessingSettingsPayload(entity.getMaxVideoMinutes());
    }

    public ProcessingSettingsPayload updateSettings(ProcessingSettingsPayload payload) {
        ProcessingSettingsEntity entity = ensureSettingsRow();
        entity.setMaxVideoMinutes(payload.maxVideoMinutes());
        entity.setUpdatedAt(LocalDateTime.now());
        processingSettingsMapper.updateById(entity);
        return payload;
    }

    private ProcessingSettingsEntity ensureSettingsRow() {
        ProcessingSettingsEntity existing = processingSettingsMapper.selectById(SETTINGS_ROW_ID);
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        ProcessingSettingsEntity created = ProcessingSettingsEntity.builder()
            .id(SETTINGS_ROW_ID)
            .maxVideoMinutes(appProperties.getProcessing().getMaxVideoMinutes())
            .createdAt(now)
            .updatedAt(now)
            .build();
        processingSettingsMapper.insert(created);
        return created;
    }
}
