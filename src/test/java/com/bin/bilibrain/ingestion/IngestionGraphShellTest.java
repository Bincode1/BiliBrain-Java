package com.bin.bilibrain.ingestion;

import com.bin.bilibrain.bilibili.BilibiliSubtitleClient;
import com.bin.bilibrain.bilibili.BilibiliSubtitlePayload;
import com.bin.bilibrain.entity.Transcript;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.entity.VideoPipeline;
import com.bin.bilibrain.graph.ingestion.IngestionGraphRunner;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.processing.subtitle-first-enabled=true")
class IngestionGraphShellTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private IngestionGraphRunner ingestionGraphRunner;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private TranscriptMapper transcriptMapper;

    @Autowired
    private VideoPipelineMapper videoPipelineMapper;

    @Autowired
    private BilibiliSubtitleClient bilibiliSubtitleClient;

    @Test
    void graphUsesSubtitleFirstPathWhenCcSubtitleExists() {
        insertVideo("BV1graph11111", 600, 24680L);
        when(bilibiliSubtitleClient.fetchSubtitle("BV1graph11111", 24680L))
            .thenReturn(Optional.of(new BilibiliSubtitlePayload(
                24680L,
                "bilibili-cc",
                "第一句\n\n第二句",
                List.of(
                    new BilibiliSubtitlePayload.Segment(0, 3.2, "第一句"),
                    new BilibiliSubtitlePayload.Segment(3.2, 7.6, "第二句")
                )
            )));

        ingestionGraphRunner.run("BV1graph11111");

        Transcript transcript = transcriptMapper.findByBvid("BV1graph11111");
        VideoPipeline pipeline = videoPipelineMapper.selectById("BV1graph11111");
        Video refreshedVideo = videoMapper.selectById("BV1graph11111");

        assertThat(transcript).isNotNull();
        assertThat(transcript.getSourceModel()).isEqualTo("bilibili-cc");
        assertThat(transcript.getSegmentCount()).isEqualTo(2);
        assertThat(transcript.getTranscriptText()).contains("第一句");
        assertThat(pipeline).isNotNull();
        assertThat(pipeline.getOverallStatus()).isEqualTo("partial");
        assertThat(pipeline.getStateJson()).contains("\"audio\":{\"status\":\"done\"");
        assertThat(pipeline.getStateJson()).contains("\"transcript\":{\"status\":\"done\"");
        assertThat(refreshedVideo.getSubtitleSource()).isEqualTo("bilibili-cc");
        assertThat(refreshedVideo.getSyncedAt()).isNotNull();
    }

    @Test
    void graphFailsFastWhenVideoExceedsDurationLimit() {
        insertVideo("BV1graph22222", 7200, 13579L);

        assertThatThrownBy(() -> ingestionGraphRunner.run("BV1graph22222"))
            .hasMessageContaining("视频时长超过");

        Transcript transcript = transcriptMapper.findByBvid("BV1graph22222");
        VideoPipeline pipeline = videoPipelineMapper.selectById("BV1graph22222");

        assertThat(transcript).isNull();
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

    @TestConfiguration
    static class GraphShellTestConfig {
        @Bean
        @Primary
        BilibiliSubtitleClient bilibiliSubtitleClient() {
            return mock(BilibiliSubtitleClient.class);
        }
    }
}
