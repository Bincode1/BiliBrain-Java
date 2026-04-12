package com.bin.bilibrain.manager.bilibili;

import com.bin.bilibrain.bilibili.BilibiliCredential;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.service.system.AppStateService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BilibiliCredentialManager {
    private static final String STATE_KEY = "bilibili_credentials";
    private static final String LEGACY_STATE_KEY = "auth_cookies";
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

    public BilibiliCredential loadCredential() {
        return loadConfiguredCredential().merge(loadPersistedCredential());
    }

    public void saveCredential(Map<String, String> credentialValues) {
        BilibiliCredential merged = loadPersistedCredential().merge(filterSupported(credentialValues));
        if (!merged.isEmpty()) {
            appStateService.saveJson(STATE_KEY, merged.asRequestCookies());
        }
    }

    public BilibiliCredential loadPersistedCredential() {
        BilibiliCredential current = loadCredentialByKey(STATE_KEY);
        return current.isEmpty() ? loadCredentialByKey(LEGACY_STATE_KEY) : current;
    }

    private BilibiliCredential loadConfiguredCredential() {
        Map<String, String> configured = new LinkedHashMap<>();
        addCookieIfPresent(configured, "SESSDATA", appProperties.getBilibili().getSessdata());
        addCookieIfPresent(configured, "bili_jct", appProperties.getBilibili().getBiliJct());
        addCookieIfPresent(configured, "DedeUserID", appProperties.getBilibili().getDedeUserId());
        return filterSupported(configured);
    }

    private BilibiliCredential loadCredentialByKey(String stateKey) {
        Map<String, String> stored = appStateService.loadJson(stateKey, COOKIE_MAP_TYPE, LinkedHashMap::new);
        Map<String, String> normalized = new LinkedHashMap<>();
        stored.forEach((name, value) -> {
            if (SUPPORTED_COOKIE_NAMES.contains(name) && StringUtils.hasText(value)) {
                normalized.put(name, value.trim());
            }
        });
        return new BilibiliCredential(normalized);
    }

    private BilibiliCredential filterSupported(Map<String, String> values) {
        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            if (SUPPORTED_COOKIE_NAMES.contains(name) && StringUtils.hasText(value)) {
                normalized.put(name, value.trim());
            }
        });
        return new BilibiliCredential(normalized);
    }

    private void addCookieIfPresent(Map<String, String> target, String name, String value) {
        if (StringUtils.hasText(value)) {
            target.put(name, value.trim());
        }
    }
}

