package com.bin.bilibrain.service.system;

import com.bin.bilibrain.mapper.AppStateMapper;
import com.bin.bilibrain.model.entity.AppState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AppStateService {
    private final AppStateMapper appStateMapper;
    private final ObjectMapper objectMapper;

    public <T> T loadJson(String stateKey, TypeReference<T> typeReference, Supplier<T> defaultSupplier) {
        return loadJsonOptional(stateKey, typeReference).orElseGet(defaultSupplier);
    }

    public <T> Optional<T> loadJsonOptional(String stateKey, TypeReference<T> typeReference) {
        AppState entity = appStateMapper.selectById(stateKey);
        if (entity == null || !StringUtils.hasText(entity.getStateValue())) {
            return Optional.empty();
        }
        return Optional.of(readJson(entity, stateKey, typeReference));
    }

    public void delete(String stateKey) {
        appStateMapper.deleteById(stateKey);
    }

    private <T> T readJson(AppState entity, String stateKey, TypeReference<T> typeReference) {
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
        AppState existing = appStateMapper.selectById(stateKey);
        if (existing == null) {
            appStateMapper.insert(AppState.builder()
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

    public Optional<LocalDateTime> getUpdatedAt(String stateKey) {
        AppState entity = appStateMapper.selectById(stateKey);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entity.getUpdatedAt());
    }
}


