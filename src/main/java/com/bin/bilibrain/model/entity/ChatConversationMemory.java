package com.bin.bilibrain.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("chat_conversation_memory")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversationMemory {
    @TableId(value = "conversation_id", type = IdType.INPUT)
    private String conversationId;

    private String memoryText;
    private Integer sourceMessageCount;
    private LocalDateTime updatedAt;
}
