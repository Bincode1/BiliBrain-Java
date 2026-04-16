package com.bin.bilibrain.bilibili;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.manager.bilibili.BilibiliCredentialManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.Exceptions;
import reactor.util.retry.Retry;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Slf4j
@Component
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
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final WebClient bilibiliWebClient;
    private final BilibiliCredentialManager bilibiliCredentialManager;
    private final Duration requestTimeout;
    private final int requestRetries;
    private final Duration retryBackoff;

    public DefaultBilibiliMetadataClient(
        @Qualifier("bilibiliWebClient") WebClient bilibiliWebClient,
        BilibiliCredentialManager bilibiliCredentialManager,
        AppProperties appProperties
    ) {
        this.bilibiliWebClient = bilibiliWebClient;
        this.bilibiliCredentialManager = bilibiliCredentialManager;
        this.requestTimeout = Duration.ofSeconds(appProperties.getBilibili().getHttpTimeoutSeconds());
        this.requestRetries = appProperties.getBilibili().getHttpRetries();
        this.retryBackoff = Duration.ofMillis(appProperties.getBilibili().getHttpRetryBackoffMillis());
    }

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
    public BilibiliSearchResult searchVideos(String keyword, int page, int pageSize) {
        String normalizedKeyword = normalizeKeyword(keyword);
        Map<String, Object> payload = getJson(
            "https://api.bilibili.com/x/web-interface/search/type",
            Map.of(
                "search_type", "video",
                "keyword", normalizedKeyword,
                "page", Math.max(page, 1),
                "page_size", Math.min(Math.max(pageSize, 1), 30),
                "order", "totalrank"
            )
        );
        Map<String, Object> data = asMap(payload.get("data"));
        List<?> items = asList(data.get("result"));
        List<BilibiliSearchVideo> results = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> row = asMap(item);
            String bvid = asString(row.get("bvid")).trim();
            if (!StringUtils.hasText(bvid)) {
                continue;
            }
            long publishedAt = asLong(row.get("pubdate"));
            results.add(new BilibiliSearchVideo(
                bvid,
                stripHtml(row.get("title")),
                stripHtml(row.get("author")),
                stripHtml(row.get("description")),
                resolveCoverUrl(asString(row.get("pic"))),
                asString(row.get("duration")).trim(),
                asInt(row.get("play")),
                asInt(row.get("favorites")),
                stripHtml(row.get("tag")),
                publishedAt > 0 ? LocalDateTime.ofInstant(Instant.ofEpochSecond(publishedAt), ZoneOffset.UTC) : null,
                "https://www.bilibili.com/video/" + bvid + "/"
            ));
        }
        return new BilibiliSearchResult(
            normalizedKeyword,
            asInt(data.get("page")) == 0 ? Math.max(page, 1) : asInt(data.get("page")),
            asInt(data.get("pagesize")) == 0 ? Math.min(Math.max(pageSize, 1), 30) : asInt(data.get("pagesize")),
            asInt(data.get("numResults")),
            results
        );
    }

    @Override
    public BilibiliAudioTrack downloadAudioTrack(String bvid, Path outputPath) {
        if (!StringUtils.hasText(bvid)) {
            throw new BilibiliClientException("bvid 不能为空。");
        }
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        long startedAt = System.nanoTime();
        try {
            Files.createDirectories(normalizedOutput.getParent());
            BilibiliAudioTrack track = resolveAudioTrack(bvid.trim());
            DataBufferUtils.write(
                bilibiliWebClient
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
                    .bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class)
                    .timeout(requestTimeout)
                    .doOnSubscribe(ignored -> log.info(
                        "client=bilibili operation=download-audio status=started bvid={} audioUrl={}",
                        bvid.trim(),
                        track.audioUrl()
                    ))
                    .doOnComplete(() -> log.info(
                        "client=bilibili operation=download-audio status=success bvid={} elapsedMs={}",
                        bvid.trim(),
                        elapsedMs(startedAt)
                    ))
                    .doOnError(exception -> log.warn(
                        "client=bilibili operation=download-audio status=failed bvid={} elapsedMs={} exceptionClass={} message={}",
                        bvid.trim(),
                        elapsedMs(startedAt),
                        rootCause(exception).getClass().getSimpleName(),
                        messageOf(exception),
                        exception
                    )),
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
            Map<?, ?> payload = bilibiliWebClient
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
                .onStatus(
                    status -> status.value() == 429 || status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new RetryableBilibiliHttpException(uri, clientResponse.statusCode().value(), body))
                )
                .onStatus(
                    org.springframework.http.HttpStatusCode::isError,
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new BilibiliClientException(
                            "请求 Bilibili 失败: uri=%s, status=%s, body=%s"
                                .formatted(uri, clientResponse.statusCode().value(), body)
                        ))
                )
                .bodyToMono(Map.class)
                .timeout(requestTimeout)
                .doOnSubscribe(ignored -> log.info("client=bilibili operation=json-get status=started uri={}", uri))
                .retryWhen(buildRetrySpec("json-get", uri))
                .doOnSuccess(ignored -> log.info("client=bilibili operation=json-get status=success uri={}", uri))
                .doOnError(exception -> log.warn(
                    "client=bilibili operation=json-get status=failed uri={} exceptionClass={} message={}",
                    uri,
                    rootCause(exception).getClass().getSimpleName(),
                    messageOf(exception),
                    exception
                ))
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

    private Map<String, Object> postJson(String url, Map<String, Object> params) {
        try {
            Map<?, ?> payload = bilibiliWebClient
                .post()
                .uri(url)
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(buildFormBody(params))
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
                .onStatus(
                    status -> status.value() == 429 || status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new RetryableBilibiliHttpException(
                            URI.create(url), clientResponse.statusCode().value(), body))
                )
                .onStatus(
                    org.springframework.http.HttpStatusCode::isError,
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new BilibiliClientException(
                            "POST 请求 Bilibili 失败: uri=%s, status=%s, body=%s"
                                .formatted(url, clientResponse.statusCode().value(), body)
                        ))
                )
                .bodyToMono(Map.class)
                .timeout(requestTimeout)
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
            throw new BilibiliClientException("POST 请求 Bilibili 失败。", exception);
        }
    }

    private String buildFormBody(Map<String, Object> params) {
        return params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(asString(entry.getValue())))
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    }

    @Override
    public void addToFavorite(String bvid, long mediaId, int action) {
        Map<String, Object> params = signedParams(Map.of(
            "bvid", bvid,
            "media_id", mediaId,
            "action", action
        ));
        params.put("csrf", bilibiliCredentialManager.loadCredential().values().get("bili_jct"));
        postJson("https://api.bilibili.com/x/v2/fav/action", params);
    }

    private Retry buildRetrySpec(String operation, URI uri) {
        return Retry.backoff(requestRetries, retryBackoff)
            .filter(this::isRetryable)
            .doBeforeRetry(signal -> log.warn(
                "client=bilibili operation={} status=retry uri={} attempt={} retryCount={} exceptionClass={} message={}",
                operation,
                uri,
                signal.totalRetries() + 2,
                signal.totalRetries() + 1,
                rootCause(signal.failure()).getClass().getSimpleName(),
                messageOf(signal.failure())
            ));
    }

    private boolean isRetryable(Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        return rootCause instanceof RetryableBilibiliHttpException
            || rootCause instanceof WebClientRequestException
            || rootCause instanceof TimeoutException;
    }

    private Throwable rootCause(Throwable throwable) {
        return Exceptions.unwrap(throwable);
    }

    private String messageOf(Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        return rootCause.getMessage() == null ? "" : rootCause.getMessage();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
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

    private String normalizeKeyword(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().replaceAll("\\s+", " ");
        if (!StringUtils.hasText(normalized)) {
            throw new BilibiliClientException("搜索关键词不能为空。");
        }
        return normalized;
    }

    private String stripHtml(Object value) {
        String raw = HtmlUtils.htmlUnescape(asString(value));
        return HTML_TAG_PATTERN.matcher(raw).replaceAll(" ").replaceAll("\\s+", " ").trim();
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

    private static final class RetryableBilibiliHttpException extends BilibiliClientException {
        private RetryableBilibiliHttpException(URI uri, int status, String body) {
            super("请求 Bilibili 失败: uri=%s, status=%s, body=%s".formatted(uri, status, body));
        }
    }
}
