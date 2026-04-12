package com.bin.bilibrain.ingestion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bin.bilibrain.model.entity.IngestionTask;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.entity.VideoPipeline;
import com.bin.bilibrain.mapper.IngestionTaskMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
import com.bin.bilibrain.service.asr.AudioTranscriptionService;
import com.bin.bilibrain.service.ingestion.IngestionDispatcherService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class IngestionDispatcherServiceTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private IngestionDispatcherService ingestionDispatcherService;

    @Autowired
    private IngestionTaskMapper ingestionTaskMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private VideoPipelineMapper videoPipelineMapper;

    @Autowired
    private AudioDownloadService audioDownloadService;

    @Autowired
    private AudioTranscriptionService audioTranscriptionService;

    @BeforeEach
    void setUp() throws Exception {
        reset(audioDownloadService, audioTranscriptionService);
        when(audioDownloadService.download(anyString())).thenAnswer(invocation -> {
            Path tempFile = Files.createTempFile("dispatcher-audio-", ".m4a");
            Files.writeString(tempFile, "fake-audio");
            return new AudioDownloadService.DownloadedAudio(tempFile, 9988L, "audio/mp4", 64000, "30216");
        });
        when(audioTranscriptionService.resolveSourceModel()).thenReturn("dashscope/paraformer-v2");
        when(audioTranscriptionService.transcribe(any(Path.class), any())).thenReturn(
            new AudioTranscriptionService.AudioTranscriptionResult(
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
            )
        );
    }

    @Test
    void dispatchNowProcessesQueuedTaskIntoPartialPipeline() {
        insertVideo("BV1dispatch111", 600);
        insertQueuedTask("BV1dispatch111");

        ingestionDispatcherService.dispatchNow();
        waitUntil(() -> {
            IngestionTask task = latestTask("BV1dispatch111");
            return task != null && "succeeded".equals(task.getStatus());
        });

        VideoPipeline pipeline = videoPipelineMapper.selectById("BV1dispatch111");
        IngestionTask task = latestTask("BV1dispatch111");
        assertThat(task.getStatus()).isEqualTo("succeeded");
        assertThat(pipeline).isNotNull();
        assertThat(pipeline.getOverallStatus()).isEqualTo("partial");
        assertThat(pipeline.getStateJson()).contains("\"audio\":{\"status\":\"done\"");
        assertThat(pipeline.getStateJson()).contains("\"transcript\":{\"status\":\"done\"");
    }

    @Test
    void dispatchNowFailsWhenVideoExceedsDurationLimit() {
        insertVideo("BV1dispatch222", 7200);
        insertQueuedTask("BV1dispatch222");

        ingestionDispatcherService.dispatchNow();
        waitUntil(() -> {
            IngestionTask task = latestTask("BV1dispatch222");
            return task != null && "failed".equals(task.getStatus());
        });

        IngestionTask task = latestTask("BV1dispatch222");
        VideoPipeline pipeline = videoPipelineMapper.selectById("BV1dispatch222");
        assertThat(task.getStatus()).isEqualTo("failed");
        assertThat(task.getErrorMsg()).contains("视频时长超过");
        assertThat(pipeline.getOverallStatus()).isEqualTo("failed");
    }

    private void insertVideo(String bvid, int duration) {
        videoMapper.insert(Video.builder()
            .bvid(bvid)
            .folderId(3003L)
            .title("Dispatcher Test")
            .upName("BinCode")
            .duration(duration)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .isInvalid(0)
            .build());
    }

    private void insertQueuedTask(String bvid) {
        ingestionTaskMapper.insert(IngestionTask.builder()
            .bvid(bvid)
            .operation("process")
            .status("queued")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());
    }

    private IngestionTask latestTask(String bvid) {
        return ingestionTaskMapper.selectList(
                new LambdaQueryWrapper<IngestionTask>()
                    .eq(IngestionTask::getBvid, bvid)
                    .orderByDesc(IngestionTask::getTaskId)
                    .last("LIMIT 1")
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    private void waitUntil(Check check) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (check.done()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test interrupted", exception);
            }
        }
        throw new AssertionError("condition was not satisfied within timeout");
    }

    @FunctionalInterface
    private interface Check {
        boolean done();
    }

    @TestConfiguration
    static class DispatcherServiceTestConfig {
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

