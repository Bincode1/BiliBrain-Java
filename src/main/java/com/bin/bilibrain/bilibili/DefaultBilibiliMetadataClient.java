package com.bin.bilibrain.bilibili;

import com.bin.bilibrain.manager.bilibili.BilibiliCredentialManager;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class DefaultBilibiliMetadataClient implements BilibiliMetadataClient {
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    private static final int[] MIXIN_KEY_ENC_TAB = {
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    private final WebClient.Builder webClientBuilder;
    private final BilibiliCredentialManager bilibiliCredentialManager;

    @Override
    public List<BilibiliFolderMetadata> listFolders(long uid) {
        Map<String, Object> payload = getJson(
            "https://api.bilibili.com/x/v3/fav/folder/created/list-all",
            signedParams(Map.of("up_mid", uid))
        );
        Object data = payload.get("data");
        List<?> items;
        if (data instanceof List<?> dataList) {
            items = dataList;
        } else {
            items = asList(asMap(data).get("list"));
        }

        List<BilibiliFolderMetadata> folders = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> row = asMap(item);
            folders.add(new BilibiliFolderMetadata(
                asLong(row.get("id")),
                asString(row.get("title")),
                asInt(row.get("media_count"))
            ));
        }
        return folders;
    }

    @Override
    public List<BilibiliVideoMetadata> listFolderVideos(long folderId) {
        List<BilibiliVideoMetadata> videos = new ArrayList<>();
        int page = 1;
        while (true) {
            Map<String, Object> payload = getJson(
                "https://api.bilibili.com/x/v3/fav/resource/list",
                signedParams(Map.of(
                    "media_id", folderId,
                    "pn", page,
                    "ps", 20,
                    "platform", "web",
                    "keyword", "",
                    "order", "mtime",
                    "type", 0,
                    "tid", 0
                ))
            );
            Map<String, Object> data = asMap(payload.get("data"));
            List<?> medias = asList(data.get("medias"));
            for (int index = 0; index < medias.size(); index++) {
                Map<String, Object> media = asMap(medias.get(index));
                String rawBvid = asString(media.get("bvid")).trim();
                String resourceId = StringUtils.hasText(asString(media.get("id")))
                    ? asString(media.get("id"))
                    : "page" + page + "-idx" + index;
                boolean invalid = !StringUtils.hasText(rawBvid);
                String bvid = invalid ? "invalid:" + folderId + ":" + resourceId : rawBvid;
                Map<String, Object> upper = asMap(media.get("upper"));
                long pubtime = asLong(media.get("pubtime"));

                videos.add(new BilibiliVideoMetadata(
                    bvid,
                    StringUtils.hasText(asString(media.get("title")))
                        ? asString(media.get("title"))
                        : (invalid ? "已失效视频" : bvid),
                    StringUtils.hasText(asString(upper.get("name")))
                        ? asString(upper.get("name"))
                        : (invalid ? "视频已失效" : ""),
                    resolveCoverUrl(asString(media.get("cover"))),
                    asInt(media.get("duration")),
                    invalid,
                    pubtime > 0 ? LocalDateTime.ofInstant(Instant.ofEpochSecond(pubtime), ZoneOffset.UTC) : null
                ));
            }
            if (medias.isEmpty() || !asBoolean(data.get("has_more"))) {
                break;
            }
            page += 1;
        }
        return videos;
    }

    @Override
    public BilibiliAudioTrack downloadAudioTrack(String bvid, Path outputPath) {
        if (!StringUtils.hasText(bvid)) {
            throw new BilibiliClientException("bvid 不能为空。");
        }
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalizedOutput.getParent());
            BilibiliAudioTrack track = resolveAudioTrack(bvid.trim());
            DataBufferUtils.write(
                webClientBuilder.build()
                    .get()
                    .uri(track.audioUrl())
                    .headers(headers -> {
                        headers.set("User-Agent", USER_AGENT);
                        headers.set("Referer", "https://www.bilibili.com/video/" + bvid.trim() + "/");
                        headers.set("Origin", "https://www.bilibili.com");
                        headers.set("Accept", "*/*");
                        String cookieHeader = buildCookieHeader();
                        if (StringUtils.hasText(cookieHeader)) {
                            headers.set("Cookie", cookieHeader);
                        }
                    })
                    .retrieve()
                    .bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class),
                normalizedOutput,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            ).block();
            return track;
        } catch (BilibiliClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BilibiliClientException("下载 Bilibili 音频失败。", exception);
        }
    }

    private BilibiliAudioTrack resolveAudioTrack(String bvid) {
        Map<String, Object> viewPayload = getJson(
            "https://api.bilibili.com/x/web-interface/view",
            signedParams(Map.of("bvid", bvid))
        );
        Map<String, Object> viewData = asMap(viewPayload.get("data"));
        long cid = asLong(viewData.get("cid"));
        if (cid <= 0) {
            List<?> pages = asList(viewData.get("pages"));
            if (!pages.isEmpty()) {
                cid = asLong(asMap(pages.get(0)).get("cid"));
            }
        }
        if (cid <= 0) {
            throw new BilibiliClientException("当前视频没有可用 cid，无法提取音频。");
        }

        Map<String, Object> playurlPayload = getJson(
            "https://api.bilibili.com/x/player/playurl",
            Map.of(
                "bvid", bvid,
                "cid", cid,
                "qn", 64,
                "fnval", 16,
                "fourk", 1
            )
        );
        Map<String, Object> playurlData = asMap(playurlPayload.get("data"));
        Map<String, Object> dash = asMap(playurlData.get("dash"));
        List<?> audioTracks = asList(dash.get("audio"));
        if (audioTracks.isEmpty()) {
            throw new BilibiliClientException("当前视频没有可用音频流。");
        }

        Map<String, Object> chosen = audioTracks.stream()
            .map(this::asMap)
            .min(Comparator.comparingLong(track -> asLong(track.get("bandwidth"))))
            .orElseThrow(() -> new BilibiliClientException("当前视频没有可用音频流。"));

        String audioUrl = asString(chosen.get("baseUrl"));
        if (!StringUtils.hasText(audioUrl)) {
            audioUrl = asString(chosen.get("base_url"));
        }
        if (!StringUtils.hasText(audioUrl)) {
            List<?> backups = asList(chosen.get("backupUrl"));
            if (backups.isEmpty()) {
                backups = asList(chosen.get("backup_url"));
            }
            if (!backups.isEmpty()) {
                audioUrl = asString(backups.get(0));
            }
        }
        if (!StringUtils.hasText(audioUrl)) {
            throw new BilibiliClientException("当前音频流 URL 为空。");
        }

        return new BilibiliAudioTrack(
            cid,
            audioUrl,
            asString(chosen.get("mimeType")).isBlank()
                ? defaultMimeType(asString(chosen.get("mime_type")))
                : asString(chosen.get("mimeType")),
            asInt(chosen.get("bandwidth")),
            asString(chosen.get("id"))
        );
    }

    private String defaultMimeType(String rawMimeType) {
        return StringUtils.hasText(rawMimeType) ? rawMimeType : "audio/mp4";
    }

    private Map<String, Object> signedParams(Map<String, Object> params) {
        Map<String, String> requestParams = new LinkedHashMap<>();
        params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
            .forEach(entry -> requestParams.put(entry.getKey(), cleanWbiValue(entry.getValue())));
        requestParams.put("wts", String.valueOf(Instant.now().getEpochSecond()));

        String mixinKey = loadMixinKey();
        String query = encodeQuery(requestParams);
        requestParams.put("w_rid", md5Hex(query + mixinKey));
        return new LinkedHashMap<>(requestParams);
    }

    private String loadMixinKey() {
        Map<String, Object> payload = getJson("https://api.bilibili.com/x/web-interface/nav", Map.of());
        Map<String, Object> data = asMap(payload.get("data"));
        Map<String, Object> wbiImg = asMap(data.get("wbi_img"));
        String imgKey = extractFileStem(asString(wbiImg.get("img_url")));
        String subKey = extractFileStem(asString(wbiImg.get("sub_url")));
        if (!StringUtils.hasText(imgKey) || !StringUtils.hasText(subKey)) {
            throw new BilibiliClientException("Bilibili 未返回有效的 WBI key。");
        }

        String raw = imgKey + subKey;
        StringBuilder builder = new StringBuilder();
        for (int index : MIXIN_KEY_ENC_TAB) {
            if (index >= 0 && index < raw.length()) {
                builder.append(raw.charAt(index));
            }
        }
        return builder.substring(0, Math.min(builder.length(), 32));
    }

    private String extractFileStem(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (slash < 0 || dot <= slash) {
            return "";
        }
        return url.substring(slash + 1, dot);
    }

    private Map<String, Object> getJson(String url, Map<String, Object> params) {
        URI uri = buildUri(url, params);
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
            long code = asLong(payload.get("code"));
            if (code != 0L) {
                throw new BilibiliClientException(
                    StringUtils.hasText(asString(payload.get("message")))
                        ? asString(payload.get("message"))
                        : "Bilibili API 返回失败。"
                );
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> castPayload = (Map<String, Object>) payload;
            return castPayload;
        } catch (BilibiliClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BilibiliClientException("请求 Bilibili 失败。", exception);
        }
    }

    private URI buildUri(String url, Map<String, Object> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        params.forEach((key, value) -> builder.queryParam(key, value));
        return builder.build(true).toUri();
    }

    private String buildCookieHeader() {
        return bilibiliCredentialManager.loadCredential().toHeaderValue();
    }

    private String resolveCoverUrl(String coverUrl) {
        if (!StringUtils.hasText(coverUrl)) {
            return "";
        }
        String raw = coverUrl.trim();
        if (raw.startsWith("//")) {
            return "https:" + raw;
        }
        return raw;
    }

    private String cleanWbiValue(Object value) {
        String raw = asString(value);
        StringBuilder builder = new StringBuilder(raw.length());
        for (char character : raw.toCharArray()) {
            if ("!'()*".indexOf(character) < 0) {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private String encodeQuery(Map<String, String> params) {
        return params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String md5Hex(String value) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 不可用。", exception);
        }
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
        if (value == null) {
            return 0L;
        }
        String raw = String.valueOf(value).trim();
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private int asInt(Object value) {
        return Math.toIntExact(asLong(value));
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Objects.equals("1", String.valueOf(value)) || Boolean.parseBoolean(String.valueOf(value));
    }
}
