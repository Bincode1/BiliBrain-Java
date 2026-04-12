package com.bin.bilibrain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bin.bilibrain.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
