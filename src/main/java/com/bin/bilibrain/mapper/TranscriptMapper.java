package com.bin.bilibrain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bin.bilibrain.model.entity.Transcript;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TranscriptMapper extends BaseMapper<Transcript> {
    @Select("SELECT * FROM transcripts WHERE bvid = #{bvid} LIMIT 1")
    Transcript findByBvid(String bvid);

    @Delete("DELETE FROM transcripts WHERE bvid = #{bvid}")
    void deleteByBvid(String bvid);
}

