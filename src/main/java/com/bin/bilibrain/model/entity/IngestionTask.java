package com.bin.bilibrain.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("ingestion_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionTask {
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;

    private String bvid;
    private String operation;
    private String status;
    private String errorMsg;
    private LocalDateTime heartbeatAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

