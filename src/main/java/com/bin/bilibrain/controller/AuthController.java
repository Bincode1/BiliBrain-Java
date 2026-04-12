package com.bin.bilibrain.controller;

import com.bin.bilibrain.common.BaseResponse;
import com.bin.bilibrain.common.ResultUtils;
import com.bin.bilibrain.model.vo.auth.AuthQrPollVO;
import com.bin.bilibrain.model.vo.auth.AuthQrStartVO;
import com.bin.bilibrain.model.vo.auth.AuthSessionVO;
import com.bin.bilibrain.service.auth.AuthService;
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
    public BaseResponse<AuthSessionVO> session() {
        return ResultUtils.success(authService.getSession());
    }

    @PostMapping("/qr/start")
    public BaseResponse<AuthQrStartVO> startQrLogin() {
        return ResultUtils.success(authService.startQrLogin());
    }

    @GetMapping("/qr/poll")
    public BaseResponse<AuthQrPollVO> pollQrLogin(@RequestParam("qrcode_key") @NotBlank String qrcodeKey) {
        return ResultUtils.success(authService.pollQrLogin(qrcodeKey));
    }
}
