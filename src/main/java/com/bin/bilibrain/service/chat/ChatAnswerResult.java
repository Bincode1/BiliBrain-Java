package com.bin.bilibrain.service.chat;

import com.bin.bilibrain.model.vo.chat.ChatSourceVO;

import java.util.List;

public record ChatAnswerResult(
    String answer,
    String route,
    String mode,
    String reasoning,
    List<ChatSourceVO> sources
) {
}
