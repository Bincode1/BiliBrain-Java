package com.bin.bilibrain.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("chat_conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversation {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;
    private String conversationType;
    private Long folderId;
    private String videoBvid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
