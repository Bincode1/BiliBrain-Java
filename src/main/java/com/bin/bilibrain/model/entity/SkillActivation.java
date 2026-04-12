package com.bin.bilibrain.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("skill_activations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillActivation {
    @TableId
    private String skillName;

    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
