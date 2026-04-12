package com.bin.bilibrain.integration;

import com.bin.bilibrain.mapper.FolderMapper;
import com.bin.bilibrain.mapper.IngestionTaskMapper;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoPipelineMapper;
import com.bin.bilibrain.model.entity.Folder;
import com.bin.bilibrain.model.entity.IngestionTask;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.entity.VideoPipeline;
import com.bin.bilibrain.service.ingestion.IngestionDispatcherService;
import com.bin.bilibrain.service.ingestion.PipelineStateSupport;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProcessingLifecycleTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FolderMapper folderMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private IngestionTaskMapper ingestionTaskMapper;

    @Autowired
    private TranscriptMapper transcriptMapper;

    @Autowired
    private VideoPipelineMapper videoPipelineMapper;

    @Autowired
    private PipelineStateSupport pipelineStateSupport;

    @MockitoBean
    private IngestionDispatcherService ingestionDispatcherService;

    @Test
    void processStatusTransitionsFromQueuedToIndexed() throws Exception {
        String bvid = "BV1process01";
        seedVideo(bvid);
        when(ingestionDispatcherService.hasRunningTask(anyString())).thenReturn(false);

        mockMvc.perform(post("/api/videos/{bvid}/process", bvid))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.started").value(true))
            .andExpect(jsonPath("$.data.overall_status").value("processing"))
            .andExpect(jsonPath("$.data.queue_status").value("queued"));

        IngestionTask task = ingestionTaskMapper.findLatestActiveByBvid(bvid);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("succeeded");
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        ingestionTaskMapper.updateById(task);

        Video video = videoMapper.selectById(bvid);
        video.setAudioStorageProvider("local");
        video.setAudioObjectKey("audio/BV1process01.mp3");
        video.setAudioUploadedAt(now);
        video.setUpdatedAt(now);
        videoMapper.updateById(video);

        transcriptMapper.insert(Transcript.builder()
            .bvid(bvid)
            .sourceModel("paraformer-v2")
            .segmentCount(4)
            .transcriptText("先下载音频，再做切片转写，最后写入向量库。")
            .segmentsJson("[]")
            .createdAt(now)
            .updatedAt(now)
            .build());

        Map<String, Map<String, Object>> state = pipelineStateSupport.defaultState();
        pipelineStateSupport.markAudioDone(state, "audio/BV1process01.mp3", "音频已缓存");
        pipelineStateSupport.markTranscriptDone(state, "paraformer-v2", 4);
        pipelineStateSupport.markIndexDone(state, 6, "已写入 Chroma");
        videoPipelineMapper.insert(VideoPipeline.builder()
            .bvid(bvid)
            .overallStatus("indexed")
            .stateJson(pipelineStateSupport.writeState(state))
            .updatedAt(now)
            .build());

        mockMvc.perform(get("/api/videos/{bvid}/process/status", bvid))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.overall_status").value("indexed"))
            .andExpect(jsonPath("$.data.has_transcript").value(true))
            .andExpect(jsonPath("$.data.transcript_segment_count").value(4))
            .andExpect(jsonPath("$.data.chunk_count").value(6))
            .andExpect(jsonPath("$.data.audio_storage_provider").value("local"))
            .andExpect(jsonPath("$.data.running").value(false));
    }

    private void seedVideo(String bvid) {
        LocalDateTime now = LocalDateTime.now();
        folderMapper.insert(Folder.builder()
            .folderId(93001L)
            .uid(778899L)
            .title("处理中收藏夹")
            .mediaCount(1)
            .createdAt(now)
            .updatedAt(now)
            .build());
        videoMapper.insert(Video.builder()
            .bvid(bvid)
            .folderId(93001L)
            .title("处理生命周期测试")
            .upName("BinCode")
            .coverUrl("https://example.com/cover.jpg")
            .duration(480)
            .createdAt(now)
            .updatedAt(now)
            .isInvalid(0)
            .build());
    }
}
