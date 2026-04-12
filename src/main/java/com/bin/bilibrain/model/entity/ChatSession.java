package com.bin.bilibrain.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@TableName("chat_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;             // 会话标题
    private String videoBvid;         // 关联视频 (可选)
    private String type;              // VIDEO / GENERAL

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
