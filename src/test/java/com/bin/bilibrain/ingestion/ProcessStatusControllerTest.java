package com.bin.bilibrain.ingestion;

import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.entity.VideoPipeline;
import com.bin.bilibrain.model.entity.VideoSummary;
import com.bin.bilibrain.model.vo.ingestion.ProcessStatusResponse;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
import com.bin.bilibrain.mapper.VideoSummaryMapper;
import com.bin.bilibrain.service.asr.AudioTranscriptionService;
import com.bin.bilibrain.service.ingestion.IngestionDispatcherService;
import com.bin.bilibrain.service.ingestion.PipelineStatusService;
import com.bin.bilibrain.service.media.AudioDownloadService;
import com.bin.bilibrain.service.retrieval.VectorSearchService;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ProcessStatusControllerTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private TranscriptMapper transcriptMapper;

    @Autowired
    private VideoSummaryMapper videoSummaryMapper;

    @Autowired
    private VideoPipelineMapper videoPipelineMapper;

    @Autowired
    private PipelineStatusService pipelineStatusService;

    @Autowired
    private IngestionDispatcherService ingestionDispatcherService;

    @Autowired
    private AudioDownloadService audioDownloadService;

    @Autowired
    private AudioTranscriptionService audioTranscriptionService;

    @Autowired
    private VectorSearchService vectorSearchService;

    @BeforeEach
    void setUp() throws Exception {
        reset(audioDownloadService, audioTranscriptionService, vectorSearchService);
        when(audioDownloadService.download(anyString())).thenAnswer(invocation -> {
            Path tempFile = Files.createTempFile("status-audio-", ".m4a");
            Files.writeString(tempFile, "fake-audio");
            return new AudioDownloadService.DownloadedAudio(tempFile, 112233L, "audio/mp4", 64000, "30216");
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
        when(vectorSearchService.isAvailable()).thenReturn(true);
    }

    @Test
    void processEndpointQueuesVideoAndStatusEventuallyTurnsIndexed() throws Exception {
        insertVideo("BV1process111", 900);

        mockMvc.perform(post("/api/videos/BV1process111/process"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.started").value(true))
            .andExpect(jsonPath("$.data.operation").value("process"));

        ingestionDispatcherService.dispatchNow();
        waitUntil(() -> "indexed".equals(pipelineStatusService.getStatus("BV1process111").overallStatus()));

        ProcessStatusResponse status = pipelineStatusService.getStatus("BV1process111");
        assertThat(status.overallStatus()).isEqualTo("indexed");
        assertThat(status.running()).isFalse();
        assertThat(status.steps().get(0).status()).isEqualTo("done");
        assertThat(status.steps().get(1).status()).isEqualTo("done");
        assertThat(status.steps().get(2).status()).isEqualTo("done");
        assertThat(status.hasTranscript()).isTrue();
        assertThat(status.transcriptSegmentCount()).isEqualTo(2);
        assertThat(status.chunkCount()).isEqualTo(1);
    }

    @Test
    void resetEndpointClearsArtifacts() throws Exception {
        insertVideo("BV1reset11111", 600);
        transcriptMapper.insert(Transcript.builder()
            .bvid("BV1reset11111")
            .sourceModel("manual")
            .segmentCount(2)
            .transcriptText("hello")
            .segmentsJson("[]")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());
        videoSummaryMapper.insert(VideoSummary.builder()
            .bvid("BV1reset11111")
            .transcriptHash("hash")
            .summaryText("summary")
            .updatedAt(LocalDateTime.now())
            .build());
        videoPipelineMapper.insert(VideoPipeline.builder()
            .bvid("BV1reset11111")
            .overallStatus("indexed")
            .stateJson("{\"audio\":{\"status\":\"done\"},\"transcript\":{\"status\":\"done\"},\"index\":{\"status\":\"done\"}}")
            .updatedAt(LocalDateTime.now())
            .build());

        mockMvc.perform(post("/api/videos/BV1reset11111/reset"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.reset").value(true));

        waitUntil(() -> !pipelineStatusService.getStatus("BV1reset11111").running());

        assertThat(transcriptMapper.findByBvid("BV1reset11111")).isNull();
        assertThat(videoSummaryMapper.selectById("BV1reset11111")).isNull();
        assertThat(videoPipelineMapper.selectById("BV1reset11111")).isNull();
    }

    @Test
    void resetAllEndpointClearsAllProcessingArtifacts() throws Exception {
        insertVideo("BV1resetAll01", 600);
        insertVideo("BV1resetAll02", 600);
        transcriptMapper.insert(Transcript.builder()
            .bvid("BV1resetAll01")
            .sourceModel("manual")
            .segmentCount(1)
            .transcriptText("hello")
            .segmentsJson("[]")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());

        mockMvc.perform(post("/api/videos/reset-all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.reset").value(true))
            .andExpect(jsonPath("$.data.transcript_count").value(1));

        assertThat(transcriptMapper.selectCount(null)).isZero();
    }

    @Test
    void statusEndpointReturnsNotFoundForUnknownVideo() throws Exception {
        mockMvc.perform(get("/api/videos/BV404/process/status"))
            .andExpect(status().isNotFound());
    }

    private void insertVideo(String bvid, int duration) {
        videoMapper.insert(Video.builder()
            .bvid(bvid)
            .folderId(4004L)
            .title("Process Test")
            .upName("BinCode")
            .duration(duration)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .isInvalid(0)
            .build());
    }

    private void waitUntil(Check check) {
        long deadline = System.currentTimeMillis() + 10000;
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
    static class ProcessStatusTestConfig {
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

        @Bean
        @Primary
        VectorSearchService vectorSearchService() {
            return mock(VectorSearchService.class);
        }
    }
}

