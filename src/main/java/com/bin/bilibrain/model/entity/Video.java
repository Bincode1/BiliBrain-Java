package com.bin.bilibrain.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@TableName("videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video {
    @TableId(type = IdType.INPUT)
    private String bvid;

    private Long folderId;
    private String title;
    private String upName;
    private String coverUrl;
    private Integer duration;
    private LocalDateTime publishedAt;
    private Long cid;
    private String subtitleSource;
    private String manualTags;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String audioStorageProvider;
    private String audioObjectKey;
    private LocalDateTime audioUploadedAt;
    private Integer isInvalid;
}

