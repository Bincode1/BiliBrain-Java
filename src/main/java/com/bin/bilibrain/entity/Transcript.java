package com.bin.bilibrain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@TableName("transcripts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transcript {
    @TableId(value = "bvid", type = IdType.INPUT)
    private String bvid;

    private String sourceModel;
    private Integer segmentCount;
    private String transcriptText;
    private String segmentsJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
