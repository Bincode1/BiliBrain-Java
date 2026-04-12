package com.bin.bilibrain.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@TableName("folder_videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderVideo {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long folderId;
    private String bvid;
    private LocalDateTime addedAt;
}
