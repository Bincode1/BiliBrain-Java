package com.bin.bilibrain.bilibili;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.state.AppStateService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BilibiliCookieStore {
    private static final String STATE_KEY = "auth_cookies";
    private static final TypeReference<LinkedHashMap<String, String>> COOKIE_MAP_TYPE =
        new TypeReference<>() {
        };
    private static final Set<String> SUPPORTED_COOKIE_NAMES = Set.of(
        "SESSDATA",
        "bili_jct",
        "DedeUserID",
        "DedeUserID__ckMd5",
        "buvid3",
        "b_nut"
    );

    private final AppStateService appStateService;
    private final AppProperties appProperties;

    public Map<String, String> loadCookies() {
        Map<String, String> merged = new LinkedHashMap<>();
        addCookieIfPresent(merged, "SESSDATA", appProperties.getBilibili().getSessdata());
        addCookieIfPresent(merged, "bili_jct", appProperties.getBilibili().getBiliJct());
        addCookieIfPresent(merged, "DedeUserID", appProperties.getBilibili().getDedeUserId());
        merged.putAll(loadPersistedCookies());
        return merged;
    }

    public void saveCookies(Map<String, String> cookies) {
        Map<String, String> merged = new LinkedHashMap<>(loadPersistedCookies());
        cookies.forEach((name, value) -> {
            if (SUPPORTED_COOKIE_NAMES.contains(name) && StringUtils.hasText(value)) {
                merged.put(name, value.trim());
            }
        });
        if (!merged.isEmpty()) {
            appStateService.saveJson(STATE_KEY, merged);
        }
    }

    public Map<String, String> loadPersistedCookies() {
        Map<String, String> stored = appStateService.loadJson(STATE_KEY, COOKIE_MAP_TYPE, LinkedHashMap::new);
        Map<String, String> normalized = new LinkedHashMap<>();
        stored.forEach((name, value) -> {
            if (SUPPORTED_COOKIE_NAMES.contains(name) && StringUtils.hasText(value)) {
                normalized.put(name, value.trim());
            }
        });
        return normalized;
    }

    private void addCookieIfPresent(Map<String, String> target, String name, String value) {
        if (StringUtils.hasText(value)) {
            target.put(name, value.trim());
        }
    }
}
