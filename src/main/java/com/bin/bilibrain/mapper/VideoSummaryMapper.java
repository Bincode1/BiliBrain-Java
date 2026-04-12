package com.bin.bilibrain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bin.bilibrain.model.entity.VideoSummary;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VideoSummaryMapper extends BaseMapper<VideoSummary> {
    @Delete("DELETE FROM video_summaries WHERE bvid = #{bvid}")
    void deleteByBvid(String bvid);
}

