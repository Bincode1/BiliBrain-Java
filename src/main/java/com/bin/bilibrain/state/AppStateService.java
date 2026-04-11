package com.bin.bilibrain.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AppStateService {
    private final AppStateMapper appStateMapper;
    private final ObjectMapper objectMapper;

    public <T> T loadJson(String stateKey, TypeReference<T> typeReference, Supplier<T> defaultSupplier) {
        AppStateEntity entity = appStateMapper.selectById(stateKey);
        if (entity == null || !StringUtils.hasText(entity.getStateValue())) {
            return defaultSupplier.get();
        }
        try {
            return objectMapper.readValue(entity.getStateValue(), typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取应用状态失败: " + stateKey, exception);
        }
    }

    public void saveJson(String stateKey, Object value) {
        final String serializedValue;
        try {
            serializedValue = objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("写入应用状态失败: " + stateKey, exception);
        }

        LocalDateTime now = LocalDateTime.now();
        AppStateEntity existing = appStateMapper.selectById(stateKey);
        if (existing == null) {
            appStateMapper.insert(AppStateEntity.builder()
                .stateKey(stateKey)
                .stateValue(serializedValue)
                .updatedAt(now)
                .build());
            return;
        }

        existing.setStateValue(serializedValue);
        existing.setUpdatedAt(now);
        appStateMapper.updateById(existing);
    }
}
