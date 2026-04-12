package com.bin.bilibrain.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@TableName("chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private String role;              // USER / ASSISTANT / SYSTEM

    private String content;           // 消息内容 (TEXT 类型)
    private String citations;         // JSON 格式引用信息 (TEXT 类型)

    private LocalDateTime createdAt;
}
