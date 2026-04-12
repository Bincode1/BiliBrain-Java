package com.bin.bilibrain.service.system;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.mapper.ProcessingSettingsMapper;
import com.bin.bilibrain.model.dto.system.ProcessingSettingsUpdateRequest;
import com.bin.bilibrain.model.entity.ProcessingSettings;
import com.bin.bilibrain.model.vo.system.ProcessingSettingsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProcessingSettingsService {
    private static final long SETTINGS_ROW_ID = 1L;

    private final ProcessingSettingsMapper processingSettingsMapper;
    private final AppProperties appProperties;

    @Transactional
    public ProcessingSettingsVO getSettings() {
        return toVO(ensureSettingsRow());
    }

    @Transactional
    public ProcessingSettingsVO updateSettings(ProcessingSettingsUpdateRequest request) {
        ProcessingSettings entity = ensureSettingsRow();
        entity.setMaxVideoMinutes(request.maxVideoMinutes());
        entity.setUpdatedAt(LocalDateTime.now());
        processingSettingsMapper.updateById(entity);
        return toVO(entity);
    }

    private synchronized ProcessingSettings ensureSettingsRow() {
        ProcessingSettings existing = processingSettingsMapper.selectById(SETTINGS_ROW_ID);
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        ProcessingSettings created = ProcessingSettings.builder()
            .id(SETTINGS_ROW_ID)
            .maxVideoMinutes(appProperties.getProcessing().getMaxVideoMinutes())
            .createdAt(now)
            .updatedAt(now)
            .build();
        processingSettingsMapper.insert(created);
        return created;
    }

    private ProcessingSettingsVO toVO(ProcessingSettings entity) {
        return new ProcessingSettingsVO(entity.getMaxVideoMinutes());
    }
}
