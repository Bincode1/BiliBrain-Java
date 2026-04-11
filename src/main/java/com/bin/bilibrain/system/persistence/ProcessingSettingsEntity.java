package com.bin.bilibrain.system.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("processing_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingSettingsEntity {
    @TableId(type = IdType.INPUT)
    private Long id;

    private Integer maxVideoMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
