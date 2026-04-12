package com.bin.bilibrain.bilibili;

public interface BilibiliAuthClient {
    BilibiliSessionPayload fetchSession(BilibiliCredential credential);

    BilibiliQrStartPayload startQrLogin();

    BilibiliQrPollPayload pollQrLogin(String qrcodeKey);
}
