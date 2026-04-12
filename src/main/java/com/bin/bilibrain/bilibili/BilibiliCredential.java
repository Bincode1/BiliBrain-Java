package com.bin.bilibrain.bilibili;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record BilibiliCredential(Map<String, String> values) {

    public BilibiliCredential {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((name, value) -> {
                if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                    normalized.put(name.trim(), value.trim());
                }
            });
        }
        values = Collections.unmodifiableMap(normalized);
    }

    public static BilibiliCredential empty() {
        return new BilibiliCredential(Map.of());
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<String, String> asRequestCookies() {
        return values;
    }

    public String toHeaderValue() {
        return values.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((left, right) -> left + "; " + right)
            .orElse("");
    }

    public BilibiliCredential merge(BilibiliCredential override) {
        if (override == null || override.isEmpty()) {
            return this;
        }
        Map<String, String> merged = new LinkedHashMap<>(values);
        merged.putAll(override.values);
        return new BilibiliCredential(merged);
    }
}
