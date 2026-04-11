package com.bin.bilibrain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("video_summaries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoSummary {
    @TableId(value = "bvid", type = IdType.INPUT)
    private String bvid;

    private String transcriptHash;
    private String summaryText;
    private LocalDateTime updatedAt;
}
