package com.bin.bilibrain.state;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("app_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppStateEntity {
    @TableId(value = "state_key", type = IdType.INPUT)
    private String stateKey;

    private String stateValue;
    private LocalDateTime updatedAt;
}
