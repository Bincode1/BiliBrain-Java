package com.bin.bilibrain.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("tool_workspaces")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolWorkspace {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String workspaceKey;
    private String workspacePath;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
