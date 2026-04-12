package com.bin.bilibrain.ingestion;

import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.entity.VideoPipeline;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.graph.ingestion.IngestionGraphRunner;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
import com.bin.bilibrain.service.asr.AudioTranscriptionService;
import com.bin.bilibrain.service.media.AudioDownloadService;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class IngestionGraphShellTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private IngestionGraphRunner ingestionGraphRunner;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private VideoPipelineMapper videoPipelineMapper;

    @Autowired
    private TranscriptMapper transcriptMapper;

    @Autowired
    private AudioDownloadService audioDownloadService;

    @Autowired
    private AudioTranscriptionService audioTranscriptionService;

    @BeforeEach
    void setUp() throws Exception {
        reset(audioDownloadService, audioTranscriptionService);
        when(audioDownloadService.download(anyString())).thenAnswer(invocation -> {
            String bvid = invocation.getArgument(0);
            Path tempFile = Files.createTempFile("graph-audio-" + bvid + "-", ".m4a");
            Files.writeString(tempFile, "fake-audio");
            return new AudioDownloadService.DownloadedAudio(tempFile, 24680L, "audio/mp4", 64000, "30216");
        });
        when(audioTranscriptionService.resolveSourceModel()).thenReturn("dashscope/paraformer-v2");
        when(audioTranscriptionService.transcribe(any(Path.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var listener = (java.util.function.Consumer<AudioTranscriptionService.AudioTranscriptionProgress>) invocation.getArgument(1);
            listener.accept(new AudioTranscriptionService.AudioTranscriptionProgress("chunking", "正在分析静音并切分音频", 2, 0, null));
            listener.accept(new AudioTranscriptionService.AudioTranscriptionProgress("transcribing", "正在转写音频块 1/2", 2, 1, 1));
            listener.accept(new AudioTranscriptionService.AudioTranscriptionProgress("transcribing", "正在转写音频块 2/2", 2, 2, 2));
            return new AudioTranscriptionService.AudioTranscriptionResult(
                "dashscope/paraformer-v2",
                2,
                2,
                "第一段内容\n\n第二段继续",
                java.util.List.of(
                    new AudioTranscriptionService.AudioTranscriptSegment(0, 0.0, 60.0, "第一段内容"),
                    new AudioTranscriptionService.AudioTranscriptSegment(1, 60.0, 120.0, "第二段继续")
                ),
                180,
                240,
                8
            );
        });
    }

    @Test
    void graphBuildsAudioMainlineAndPersistsTranscript() {
        insertVideo("BV1graph11111", 600, 24680L);

        ingestionGraphRunner.run("BV1graph11111");

        VideoPipeline pipeline = videoPipelineMapper.selectById("BV1graph11111");
        Video refreshedVideo = videoMapper.selectById("BV1graph11111");

        assertThat(pipeline).isNotNull();
        assertThat(pipeline.getOverallStatus()).isEqualTo("partial");
        assertThat(pipeline.getStateJson()).contains("\"audio\":{\"status\":\"done\"");
        assertThat(pipeline.getStateJson()).contains("\"transcript\":{\"status\":\"done\"");
        assertThat(transcriptMapper.findByBvid("BV1graph11111")).isNotNull();
        assertThat(transcriptMapper.findByBvid("BV1graph11111").getTranscriptText()).contains("第二段继续");
        assertThat(refreshedVideo.getSubtitleSource()).isNull();
        assertThat(refreshedVideo.getAudioStorageProvider()).isEqualTo("local");
        assertThat(refreshedVideo.getAudioObjectKey()).isEqualTo("BV1graph11111.m4a");
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

    @TestConfiguration
    static class IngestionGraphTestConfig {
        @Bean
        @Primary
        AudioDownloadService audioDownloadService() {
            return mock(AudioDownloadService.class);
        }

        @Bean
        @Primary
        AudioTranscriptionService audioTranscriptionService() {
            return mock(AudioTranscriptionService.class);
        }
    }
}

