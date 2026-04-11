package com.bin.bilibrain.bilibili;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultBilibiliSubtitleClient implements BilibiliSubtitleClient {
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    private final WebClient.Builder webClientBuilder;
    private final BilibiliCookieStore bilibiliCookieStore;

    @Override
    public Optional<BilibiliSubtitlePayload> fetchSubtitle(String bvid, Long cid) {
        Long resolvedCid = resolveCid(bvid, cid);
        if (resolvedCid == null || resolvedCid <= 0) {
            return Optional.empty();
        }

        Optional<String> subtitleUrl = findSubtitleUrl(bvid, resolvedCid);
        if (subtitleUrl.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> payload = getJsonWithoutCodeCheck(subtitleUrl.get());
        List<BilibiliSubtitlePayload.Segment> segments = parseSegments(asList(payload.get("body")));
        if (segments.isEmpty()) {
            return Optional.empty();
        }

        String transcriptText = segments.stream()
            .map(BilibiliSubtitlePayload.Segment::content)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("\n\n"))
            .trim();
        if (!StringUtils.hasText(transcriptText)) {
            return Optional.empty();
        }

        return Optional.of(new BilibiliSubtitlePayload(
            resolvedCid,
            "bilibili-cc",
            transcriptText,
            segments
        ));
    }

    private Long resolveCid(String bvid, Long cid) {
        if (cid != null && cid > 0) {
            return cid;
        }

        Map<String, Object> payload = getJson(
            "https://api.bilibili.com/x/player/pagelist",
            Map.of("bvid", bvid)
        );
        List<?> pages = asList(payload.get("data"));
        if (pages.isEmpty()) {
            return null;
        }
        return asLong(asMap(pages.get(0)).get("cid"));
    }

    private Optional<String> findSubtitleUrl(String bvid, long cid) {
        List<String> candidates = List.of(
            "https://api.bilibili.com/x/player/wbi/v2",
            "https://api.bilibili.com/x/player/v2"
        );
        for (String url : candidates) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("bvid", bvid);
            params.put("cid", cid);
            try {
                Map<String, Object> payload = getJson(url, params);
                Map<String, Object> data = asMap(payload.get("data"));
                Map<String, Object> subtitle = asMap(data.get("subtitle"));
                List<?> subtitles = asList(subtitle.get("subtitles"));
                Optional<String> selected = selectSubtitleUrl(subtitles);
                if (selected.isPresent()) {
                    return selected;
                }
            } catch (Exception exception) {
                log.debug("failed to read bilibili subtitle catalog for {} via {}", bvid, url, exception);
            }
        }
        return Optional.empty();
    }

    private Optional<String> selectSubtitleUrl(List<?> subtitles) {
        return subtitles.stream()
            .map(this::asMap)
            .sorted(Comparator
                .comparingInt((Map<String, Object> row) -> isPreferredChinese(row) ? 0 : 1)
                .thenComparing(row -> asString(row.get("lan_doc"))))
            .map(row -> resolveUrl(asString(row.get("subtitle_url"))))
            .filter(StringUtils::hasText)
            .findFirst();
    }

    private boolean isPreferredChinese(Map<String, Object> row) {
        String language = asString(row.get("lan"));
        String languageDoc = asString(row.get("lan_doc"));
        return language.startsWith("zh") || languageDoc.contains("中文");
    }

    private List<BilibiliSubtitlePayload.Segment> parseSegments(List<?> body) {
        List<BilibiliSubtitlePayload.Segment> segments = new ArrayList<>();
        for (Object item : body) {
            Map<String, Object> row = asMap(item);
            String content = asString(row.get("content")).trim();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            segments.add(new BilibiliSubtitlePayload.Segment(
                asDouble(row.get("from")),
                asDouble(row.get("to")),
                content
            ));
        }
        return segments;
    }

    private Map<String, Object> getJson(String url, Map<String, Object> params) {
        return executeJson(buildUri(url, params), true);
    }

    private Map<String, Object> getJsonWithoutCodeCheck(String url) {
        return executeJson(buildUri(url, Map.of()), false);
    }

    private Map<String, Object> executeJson(URI uri, boolean checkCode) {
        try {
            Map<?, ?> payload = webClientBuilder.build()
                .get()
                .uri(uri)
                .headers(headers -> {
                    headers.set("User-Agent", USER_AGENT);
                    headers.set("Referer", "https://www.bilibili.com/");
                    headers.set("Origin", "https://www.bilibili.com");
                    headers.set("Accept", "application/json, text/plain, */*");
                    String cookieHeader = buildCookieHeader();
                    if (StringUtils.hasText(cookieHeader)) {
                        headers.set("Cookie", cookieHeader);
                    }
                })
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (payload == null) {
                throw new BilibiliClientException("Bilibili 返回空响应。");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> castPayload = (Map<String, Object>) payload;
            if (checkCode && castPayload.containsKey("code") && asLong(castPayload.get("code")) != 0L) {
                throw new BilibiliClientException(
                    StringUtils.hasText(asString(castPayload.get("message")))
                        ? asString(castPayload.get("message"))
                        : "Bilibili 字幕接口返回失败。"
                );
            }
            return castPayload;
        } catch (BilibiliClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BilibiliClientException("请求 Bilibili 字幕失败。", exception);
        }
    }

    private URI buildUri(String url, Map<String, Object> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        params.forEach(builder::queryParam);
        return builder.build(true).toUri();
    }

    private String buildCookieHeader() {
        return bilibiliCookieStore.loadCookies().entrySet().stream()
            .filter(entry -> StringUtils.hasText(entry.getValue()))
            .map(entry -> entry.getKey() + "=" + entry.getValue().trim())
            .reduce((left, right) -> left + "; " + right)
            .orElse("");
    }

    private String resolveUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String raw = url.trim();
        if (raw.startsWith("//")) {
            return "https:" + raw;
        }
        return raw;
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> cast = new LinkedHashMap<>();
            map.forEach((key, item) -> cast.put(String.valueOf(key), item));
            return cast;
        }
        return Map.of();
    }

    private List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String raw = asString(value).trim();
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String raw = asString(value).trim();
        if (!StringUtils.hasText(raw)) {
            return 0D;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            return 0D;
        }
    }
}
