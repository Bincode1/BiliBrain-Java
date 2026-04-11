package com.bin.bilibrain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bin.bilibrain.entity.VideoPipeline;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VideoPipelineMapper extends BaseMapper<VideoPipeline> {
    @Delete("DELETE FROM video_pipeline WHERE bvid = #{bvid}")
    void deleteByBvid(String bvid);
}
