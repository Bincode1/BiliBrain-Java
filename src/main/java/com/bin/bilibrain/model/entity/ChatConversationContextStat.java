package com.bin.bilibrain.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("chat_conversation_context_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversationContextStat {
    @TableId(value = "conversation_id", type = IdType.INPUT)
    private String conversationId;

    private Integer totalMessages;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime updatedAt;
}
