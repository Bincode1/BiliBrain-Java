package com.bin.bilibrain.ingestion;

import com.bin.bilibrain.entity.Transcript;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.entity.VideoPipeline;
import com.bin.bilibrain.entity.VideoSummary;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
import com.bin.bilibrain.mapper.VideoSummaryMapper;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void processEndpointQueuesVideoAndStatusEventuallyTurnsPartial() throws Exception {
        insertVideo("BV1process111", 900);

        mockMvc.perform(post("/api/videos/BV1process111/process"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.started").value(true))
            .andExpect(jsonPath("$.operation").value("process"));

        waitUntil(() -> "partial".equals(pipelineStatusService.getStatus("BV1process111").overallStatus()));

        ProcessStatusResponse status = pipelineStatusService.getStatus("BV1process111");
        assertThat(status.overallStatus()).isEqualTo("partial");
        assertThat(status.running()).isFalse();
        assertThat(status.steps().get(0).status()).isEqualTo("done");
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
            .andExpect(jsonPath("$.reset").value(true));

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
            .andExpect(jsonPath("$.reset").value(true))
            .andExpect(jsonPath("$.transcript_count").value(1));

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
}
