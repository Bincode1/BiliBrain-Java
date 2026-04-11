package com.bin.bilibrain.auth;

import com.bin.bilibrain.bilibili.BilibiliAuthClient;
import com.bin.bilibrain.bilibili.BilibiliCookieStore;
import com.bin.bilibrain.bilibili.BilibiliQrPollPayload;
import com.bin.bilibrain.bilibili.BilibiliQrStartPayload;
import com.bin.bilibrain.bilibili.BilibiliSessionPayload;
import com.bin.bilibrain.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final BilibiliAuthClient bilibiliAuthClient;
    private final BilibiliCookieStore bilibiliCookieStore;
    private final AppProperties appProperties;

    private volatile AuthSessionResponse cachedSession;
    private volatile Instant sessionCacheExpiresAt = Instant.EPOCH;

    public AuthSessionResponse getSession() {
        if (cachedSession != null && Instant.now().isBefore(sessionCacheExpiresAt)) {
            return cachedSession;
        }
        return refreshSession();
    }

    public AuthQrStartResponse startQrLogin() {
        BilibiliQrStartPayload payload = bilibiliAuthClient.startQrLogin();
        return new AuthQrStartResponse(payload.qrcodeKey(), payload.url(), payload.svg());
    }

    public AuthQrPollResponse pollQrLogin(String qrcodeKey) {
        BilibiliQrPollPayload payload = bilibiliAuthClient.pollQrLogin(qrcodeKey);
        if (!"confirmed".equals(payload.status())) {
            return new AuthQrPollResponse(payload.status(), payload.message(), null, null, null);
        }

        bilibiliCookieStore.saveCookies(payload.cookies());
        invalidateSessionCache();
        AuthSessionResponse session = refreshSession();
        return new AuthQrPollResponse("confirmed", null, session.loggedIn(), session.userName(), session.uid());
    }

    private AuthSessionResponse refreshSession() {
        var cookies = bilibiliCookieStore.loadCookies();
        if (cookies.isEmpty()) {
            return cacheSession(new AuthSessionResponse(false, null, null));
        }
        try {
            BilibiliSessionPayload payload = bilibiliAuthClient.fetchSession(cookies);
            return cacheSession(new AuthSessionResponse(payload.loggedIn(), payload.userName(), payload.uid()));
        } catch (Exception exception) {
            return cacheSession(new AuthSessionResponse(false, null, null));
        }
    }

    private AuthSessionResponse cacheSession(AuthSessionResponse session) {
        cachedSession = session;
        sessionCacheExpiresAt = Instant.now().plusSeconds(appProperties.getBilibili().getSessionCacheTtlSeconds());
        return session;
    }

    private void invalidateSessionCache() {
        cachedSession = null;
        sessionCacheExpiresAt = Instant.EPOCH;
    }
}
