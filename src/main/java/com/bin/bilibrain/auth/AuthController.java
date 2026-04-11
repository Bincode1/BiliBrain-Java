package com.bin.bilibrain.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @GetMapping("/session")
    public AuthSessionResponse session() {
        return authService.getSession();
    }

    @PostMapping("/qr/start")
    public AuthQrStartResponse startQrLogin() {
        return authService.startQrLogin();
    }

    @GetMapping("/qr/poll")
    public AuthQrPollResponse pollQrLogin(@RequestParam("qrcode_key") @NotBlank String qrcodeKey) {
        return authService.pollQrLogin(qrcodeKey);
    }
}
