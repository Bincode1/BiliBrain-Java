package com.bin.bilibrain.service.auth;

import com.bin.bilibrain.bilibili.BilibiliAuthClient;
import com.bin.bilibrain.bilibili.BilibiliCredential;
import com.bin.bilibrain.bilibili.BilibiliQrPollPayload;
import com.bin.bilibrain.bilibili.BilibiliQrStartPayload;
import com.bin.bilibrain.bilibili.BilibiliSessionPayload;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.manager.bilibili.BilibiliCredentialManager;
import com.bin.bilibrain.model.vo.auth.AuthQrPollVO;
import com.bin.bilibrain.model.vo.auth.AuthQrStartVO;
import com.bin.bilibrain.model.vo.auth.AuthSessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final BilibiliAuthClient bilibiliAuthClient;
    private final BilibiliCredentialManager bilibiliCredentialManager;
    private final AppProperties appProperties;

    private volatile AuthSessionVO cachedSession;
    private volatile Instant sessionCacheExpiresAt = Instant.EPOCH;

    public AuthSessionVO getSession() {
        if (cachedSession != null && Instant.now().isBefore(sessionCacheExpiresAt)) {
            return cachedSession;
        }
        return refreshSession();
    }

    public AuthQrStartVO startQrLogin() {
        BilibiliQrStartPayload payload = bilibiliAuthClient.startQrLogin();
        return new AuthQrStartVO(payload.qrcodeKey(), payload.url(), payload.svg());
    }

    public AuthQrPollVO pollQrLogin(String qrcodeKey) {
        BilibiliQrPollPayload payload = bilibiliAuthClient.pollQrLogin(qrcodeKey);
        if (!"confirmed".equals(payload.status())) {
            return new AuthQrPollVO(payload.status(), payload.message(), null, null, null);
        }

        bilibiliCredentialManager.saveCredential(payload.cookies());
        invalidateSessionCache();
        AuthSessionVO session = refreshSession();
        return new AuthQrPollVO("confirmed", null, session.loggedIn(), session.userName(), session.uid());
    }

    private AuthSessionVO refreshSession() {
        BilibiliCredential credential = bilibiliCredentialManager.loadCredential();
        if (credential.isEmpty()) {
            return cacheSession(new AuthSessionVO(false, null, null));
        }
        BilibiliSessionPayload payload = bilibiliAuthClient.fetchSession(credential);
        return cacheSession(new AuthSessionVO(payload.loggedIn(), payload.userName(), payload.uid()));
    }

    private AuthSessionVO cacheSession(AuthSessionVO session) {
        cachedSession = session;
        sessionCacheExpiresAt = Instant.now().plusSeconds(appProperties.getBilibili().getSessionCacheTtlSeconds());
        return session;
    }

    private void invalidateSessionCache() {
        cachedSession = null;
        sessionCacheExpiresAt = Instant.EPOCH;
    }
}
