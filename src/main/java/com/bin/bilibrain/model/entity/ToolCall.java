package com.bin.bilibrain.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("tool_calls")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolCall {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;
    private String toolName;
    private String status;
    private String requestJson;
    private String responseJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
