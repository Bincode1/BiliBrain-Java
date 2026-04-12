package com.bin.bilibrain.bilibili;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DefaultBilibiliAuthClient implements BilibiliAuthClient {
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    private final WebClient.Builder webClientBuilder;

    @Override
    public BilibiliSessionPayload fetchSession(BilibiliCredential credential) {
        Map<String, Object> payload = getJson(
            "https://api.bilibili.com/x/web-interface/nav",
            Map.of(),
            credential
        );
        Map<String, Object> data = asMap(payload.get("data"));
        return new BilibiliSessionPayload(
            asBoolean(data.get("isLogin")),
            textOrNull(data.get("uname")),
            asLong(data.get("mid")) > 0 ? asLong(data.get("mid")) : null
        );
    }

    @Override
    public BilibiliQrStartPayload startQrLogin() {
        Map<String, Object> payload = getJson(
            "https://passport.bilibili.com/x/passport-login/web/qrcode/generate",
            Map.of(),
            BilibiliCredential.empty()
        );
        Map<String, Object> data = asMap(payload.get("data"));
        String url = textOrNull(data.get("url"));
        String qrcodeKey = textOrNull(data.get("qrcode_key"));
        if (!StringUtils.hasText(url) || !StringUtils.hasText(qrcodeKey)) {
            throw new BilibiliClientException("Bilibili 二维码登录返回数据不完整。");
        }
        return new BilibiliQrStartPayload(qrcodeKey, url, renderQrCodeSvg(url));
    }

    @Override
    public BilibiliQrPollPayload pollQrLogin(String qrcodeKey) {
        if (!StringUtils.hasText(qrcodeKey)) {
            throw new BilibiliClientException("qrcode_key 不能为空。");
        }
        PollResponse pollResponse = exchangePoll(qrcodeKey.trim());
        Map<String, Object> body = pollResponse.body();
        long apiCode = asLong(body.get("code"));
        if (apiCode != 0L) {
            throw new BilibiliClientException(
                textOrNull(body.get("message")) == null ? "Bilibili API 返回失败。" : textOrNull(body.get("message"))
            );
        }
        Map<String, Object> data = asMap(body.get("data"));
        long code = asLong(data.get("code"));
        if (code == 0L) {
            return new BilibiliQrPollPayload("confirmed", null, pollResponse.cookies());
        }
        if (code == 86101L) {
            return new BilibiliQrPollPayload("pending", "等待扫码", Map.of());
        }
        if (code == 86090L) {
            return new BilibiliQrPollPayload("scanned", "已扫码，请在手机端确认", Map.of());
        }
        if (code == 86038L) {
            return new BilibiliQrPollPayload("expired", "二维码已过期，请重新生成", Map.of());
        }
        return new BilibiliQrPollPayload(
            "failed",
            textOrNull(data.get("message")) == null ? "扫码登录失败" : textOrNull(data.get("message")),
            Map.of()
        );
    }

    private PollResponse exchangePoll(String qrcodeKey) {
        return webClientBuilder.build()
            .get()
            .uri(buildUri(
                "https://passport.bilibili.com/x/passport-login/web/qrcode/poll",
                Map.of("qrcode_key", qrcodeKey)
            ))
            .headers(this::applyDefaultHeaders)
            .exchangeToMono(response -> response.bodyToMono(Map.class)
                .defaultIfEmpty(new LinkedHashMap<>())
                .map(body -> new PollResponse(
                    castPayload(body),
                    extractCookies(response)
                )))
            .blockOptional()
            .orElseThrow(() -> new BilibiliClientException("Bilibili 登录轮询返回空响应。"));
    }

    private Map<String, Object> getJson(String url, Map<String, Object> params, BilibiliCredential credential) {
        URI uri = buildUri(url, params);
        try {
            Map<?, ?> payload = webClientBuilder.build()
                .get()
                .uri(uri)
                .headers(headers -> {
                    applyDefaultHeaders(headers);
                    String cookieHeader = credential.toHeaderValue();
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
            Map<String, Object> castPayload = castPayload(payload);
            long code = asLong(castPayload.get("code"));
            if (code != 0L) {
                throw new BilibiliClientException(
                    textOrNull(castPayload.get("message")) == null
                        ? "Bilibili API 返回失败。"
                        : textOrNull(castPayload.get("message"))
                );
            }
            return castPayload;
        } catch (BilibiliClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BilibiliClientException("请求 Bilibili 失败。", exception);
        }
    }

    private void applyDefaultHeaders(org.springframework.http.HttpHeaders headers) {
        headers.set("User-Agent", USER_AGENT);
        headers.set("Referer", "https://www.bilibili.com/");
        headers.set("Origin", "https://www.bilibili.com");
        headers.set("Accept", "application/json, text/plain, */*");
    }

    private URI buildUri(String url, Map<String, Object> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        params.forEach(builder::queryParam);
        return builder.build(true).toUri();
    }

    private Map<String, String> extractCookies(ClientResponse response) {
        Map<String, String> cookies = new LinkedHashMap<>();
        response.cookies().forEach((name, values) -> {
            if (!values.isEmpty()) {
                ResponseCookie cookie = values.get(0);
                if (StringUtils.hasText(cookie.getValue())) {
                    cookies.put(name, cookie.getValue());
                }
            }
        });
        return cookies;
    }

    private String renderQrCodeSvg(String text) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0);
            StringBuilder builder = new StringBuilder();
            builder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(matrix.getWidth())
                .append(' ')
                .append(matrix.getHeight())
                .append("\" shape-rendering=\"crispEdges\">")
                .append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>");
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        builder.append("<rect x=\"")
                            .append(x)
                            .append("\" y=\"")
                            .append(y)
                            .append("\" width=\"1\" height=\"1\" fill=\"#000000\"/>");
                    }
                }
            }
            builder.append("</svg>");
            return builder.toString();
        } catch (WriterException exception) {
            throw new BilibiliClientException("生成二维码失败。", exception);
        }
    }

    private Map<String, Object> castPayload(Object payload) {
        return asMap(payload);
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> cast = new LinkedHashMap<>();
            map.forEach((key, item) -> cast.put(String.valueOf(key), item));
            return cast;
        }
        return Map.of();
    }

    private String textOrNull(Object value) {
        String raw = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(raw) ? raw : null;
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

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return "1".equals(String.valueOf(value)) || Boolean.parseBoolean(String.valueOf(value));
    }

    private record PollResponse(
        Map<String, Object> body,
        Map<String, String> cookies
    ) {
    }
}
