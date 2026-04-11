package com.bin.bilibrain.ingestion;

import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.entity.VideoPipeline;
import com.bin.bilibrain.graph.ingestion.IngestionGraphRunner;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class IngestionGraphShellTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private IngestionGraphRunner ingestionGraphRunner;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private VideoPipelineMapper videoPipelineMapper;

    @Test
    void graphBuildsAudioFirstShellWithoutSubtitleExtraction() {
        insertVideo("BV1graph11111", 600, 24680L);

        ingestionGraphRunner.run("BV1graph11111");

        VideoPipeline pipeline = videoPipelineMapper.selectById("BV1graph11111");
        Video refreshedVideo = videoMapper.selectById("BV1graph11111");

        assertThat(pipeline).isNotNull();
        assertThat(pipeline.getOverallStatus()).isEqualTo("partial");
        assertThat(pipeline.getStateJson()).contains("\"audio\":{\"status\":\"done\"");
        assertThat(pipeline.getStateJson()).contains("\"transcript\":{\"status\":\"pending\"");
        assertThat(refreshedVideo.getSubtitleSource()).isNull();
        assertThat(refreshedVideo.getSyncedAt()).isNull();
    }

    @Test
    void graphFailsFastWhenVideoExceedsDurationLimit() {
        insertVideo("BV1graph22222", 7200, 13579L);

        assertThatThrownBy(() -> ingestionGraphRunner.run("BV1graph22222"))
            .hasMessageContaining("视频时长超过");

        VideoPipeline pipeline = videoPipelineMapper.selectById("BV1graph22222");

        assertThat(pipeline).isNotNull();
        assertThat(pipeline.getOverallStatus()).isEqualTo("failed");
        assertThat(pipeline.getStateJson()).contains("视频时长超过当前全局限制");
    }

    private void insertVideo(String bvid, int duration, Long cid) {
        videoMapper.insert(Video.builder()
            .bvid(bvid)
            .folderId(5005L)
            .title("Graph Test")
            .upName("BinCode")
            .duration(duration)
            .cid(cid)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .isInvalid(0)
            .build());
    }
}
