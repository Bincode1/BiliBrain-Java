package com.bin.bilibrain.auth;

import com.bin.bilibrain.bilibili.BilibiliAuthClient;
import com.bin.bilibrain.bilibili.BilibiliQrPollPayload;
import com.bin.bilibrain.bilibili.BilibiliQrStartPayload;
import com.bin.bilibrain.bilibili.BilibiliSessionPayload;
import com.bin.bilibrain.state.AppStateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BilibiliAuthClient bilibiliAuthClient;

    @Autowired
    private AppStateMapper appStateMapper;

    @BeforeEach
    void setUp() {
        Mockito.reset(bilibiliAuthClient);
    }

    @Test
    void sessionEndpointReturnsLoggedOutWhenNoCookiesExist() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logged_in").value(false));
    }

    @Test
    void qrStartEndpointReturnsSvgPayload() throws Exception {
        Mockito.when(bilibiliAuthClient.startQrLogin()).thenReturn(
            new BilibiliQrStartPayload("qr-key", "https://example.com/qr", "<svg></svg>")
        );

        mockMvc.perform(post("/api/auth/qr/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.qrcode_key").value("qr-key"))
            .andExpect(jsonPath("$.url").value("https://example.com/qr"))
            .andExpect(jsonPath("$.svg").value("<svg></svg>"));
    }

    @Test
    void confirmedQrLoginPersistsCookiesAndRefreshesSession() throws Exception {
        Mockito.when(bilibiliAuthClient.pollQrLogin("qr-key")).thenReturn(
            new BilibiliQrPollPayload("confirmed", null, Map.of(
                "SESSDATA", "sess-token",
                "bili_jct", "csrf-token",
                "DedeUserID", "9527"
            ))
        );
        Mockito.when(bilibiliAuthClient.fetchSession(Mockito.anyMap())).thenReturn(
            new BilibiliSessionPayload(true, "BinCode", 9527L)
        );

        mockMvc.perform(get("/api/auth/qr/poll").queryParam("qrcode_key", "qr-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("confirmed"))
            .andExpect(jsonPath("$.logged_in").value(true))
            .andExpect(jsonPath("$.user_name").value("BinCode"))
            .andExpect(jsonPath("$.uid").value(9527));

        mockMvc.perform(get("/api/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logged_in").value(true))
            .andExpect(jsonPath("$.user_name").value("BinCode"))
            .andExpect(jsonPath("$.uid").value(9527));

        org.assertj.core.api.Assertions.assertThat(appStateMapper.selectById("auth_cookies")).isNotNull();
    }

    @Test
    void pendingQrLoginDoesNotPersistCookies() throws Exception {
        Mockito.when(bilibiliAuthClient.pollQrLogin("qr-key")).thenReturn(
            new BilibiliQrPollPayload("pending", "等待扫码", Map.of())
        );

        mockMvc.perform(get("/api/auth/qr/poll").queryParam("qrcode_key", "qr-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("pending"))
            .andExpect(jsonPath("$.message").value("等待扫码"));

        org.assertj.core.api.Assertions.assertThat(appStateMapper.selectById("auth_cookies")).isNull();
    }

    @TestConfiguration
    static class AuthTestConfig {
        @Bean
        @Primary
        BilibiliAuthClient bilibiliAuthClient() {
            return Mockito.mock(BilibiliAuthClient.class);
        }
    }
}
