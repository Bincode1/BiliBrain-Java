package com.bin.bilibrain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bin.bilibrain.entity.IngestionTask;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IngestionTaskMapper extends BaseMapper<IngestionTask> {
    @Select("""
        SELECT *
        FROM ingestion_tasks
        WHERE bvid = #{bvid}
          AND status IN ('queued', 'running')
        ORDER BY updated_at DESC, task_id DESC
        LIMIT 1
        """)
    IngestionTask findLatestActiveByBvid(String bvid);

    @Select("""
        SELECT *
        FROM ingestion_tasks
        WHERE status = 'queued'
        ORDER BY created_at ASC, task_id ASC
        LIMIT #{limit}
        """)
    List<IngestionTask> findQueuedTasks(int limit);

    @Delete("DELETE FROM ingestion_tasks WHERE bvid = #{bvid}")
    void deleteByBvid(String bvid);

    @Delete("DELETE FROM ingestion_tasks")
    void deleteAllTasks();
}
