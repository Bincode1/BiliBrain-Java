package com.bin.bilibrain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@TableName("folders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Folder {
    @TableId(value = "folder_id", type = IdType.INPUT)
    private Long folderId;

    private Long uid;
    private String title;
    private Integer mediaCount;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
