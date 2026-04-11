package com.bin.bilibrain.bilibili;

import java.util.Map;

public interface BilibiliAuthClient {
    BilibiliSessionPayload fetchSession(Map<String, String> cookies);

    BilibiliQrStartPayload startQrLogin();

    BilibiliQrPollPayload pollQrLogin(String qrcodeKey);
}
