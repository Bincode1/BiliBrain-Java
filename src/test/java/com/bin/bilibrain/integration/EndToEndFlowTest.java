package com.bin.bilibrain.integration;

import com.bin.bilibrain.bilibili.BilibiliAuthClient;
import com.bin.bilibrain.bilibili.BilibiliFolderMetadata;
import com.bin.bilibrain.bilibili.BilibiliMetadataClient;
import com.bin.bilibrain.bilibili.BilibiliQrPollPayload;
import com.bin.bilibrain.bilibili.BilibiliQrStartPayload;
import com.bin.bilibrain.bilibili.BilibiliSessionPayload;
import com.bin.bilibrain.bilibili.BilibiliVideoMetadata;
import com.bin.bilibrain.graph.summary.SummaryGraphRunner;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.VideoSummary;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoSummaryMapper;
import com.bin.bilibrain.service.summary.SummaryGenerationService;
import com.bin.bilibrain.service.summary.SummaryHashUtils;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EndToEndFlowTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TranscriptMapper transcriptMapper;

    @Autowired
    private VideoSummaryMapper videoSummaryMapper;

    @MockitoBean
    private BilibiliAuthClient bilibiliAuthClient;

    @MockitoBean
    private BilibiliMetadataClient bilibiliMetadataClient;

    @MockitoBean
    private SummaryGraphRunner summaryGraphRunner;

    @MockitoBean
    private SummaryGenerationService summaryGenerationService;

    @Test
    void loginFolderMetadataAndSummaryFlowWorksTogether() throws Exception {
        long uid = 778899L;
        long folderId = 88001L;
        String bvid = "BV1java888";
        String transcriptText = "Spring AI Alibaba 可以帮助我们把聊天、检索和 agent 工作流组织起来。";

        when(bilibiliAuthClient.startQrLogin()).thenReturn(
            new BilibiliQrStartPayload("qr-key", "https://example.com/qr", "<svg/>")
        );
        when(bilibiliAuthClient.pollQrLogin("qr-key")).thenReturn(
            new BilibiliQrPollPayload(
                "confirmed",
                null,
                Map.of(
                    "SESSDATA", "sess-data",
                    "bili_jct", "csrf-token",
                    "DedeUserID", String.valueOf(uid)
                )
            )
        );
        when(bilibiliAuthClient.fetchSession(any())).thenReturn(new BilibiliSessionPayload(true, "BinCode", uid));
        when(bilibiliMetadataClient.listFolders(uid)).thenReturn(
            List.of(new BilibiliFolderMetadata(folderId, "Java AI 收藏夹", 1))
        );
        when(bilibiliMetadataClient.listFolderVideos(folderId)).thenReturn(
            List.of(new BilibiliVideoMetadata(
                bvid,
                "Spring AI Alibaba 实战",
                "BinCode",
                "https://example.com/cover.jpg",
                620,
                false,
                LocalDateTime.of(2026, 4, 10, 10, 0)
            ))
        );
        when(summaryGenerationService.isAvailable()).thenReturn(true);
        when(summaryGraphRunner.run(bvid)).thenAnswer(invocation -> {
            VideoSummary summary = VideoSummary.builder()
                .bvid(bvid)
                .transcriptHash(SummaryHashUtils.sha256(transcriptText))
                .summaryText("这期视频主要讲了如何用 Spring AI Alibaba 组织聊天、RAG 和 agent 能力。")
                .updatedAt(LocalDateTime.now())
                .build();
            videoSummaryMapper.insert(summary);
            return new SummaryGraphRunner.SummaryRunResult(summary, true);
        });

        mockMvc.perform(post("/api/auth/qr/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.qrcode_key").value("qr-key"));

        mockMvc.perform(get("/api/auth/qr/poll").param("qrcode_key", "qr-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("confirmed"))
            .andExpect(jsonPath("$.data.logged_in").value(true))
            .andExpect(jsonPath("$.data.uid").value(uid));

        mockMvc.perform(get("/api/folders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cached").value(false))
            .andExpect(jsonPath("$.data.stale").value(false))
            .andExpect(jsonPath("$.data.folders[0].folder_id").value(folderId))
            .andExpect(jsonPath("$.data.folders[0].title").value("Java AI 收藏夹"));

        mockMvc.perform(post("/api/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"folder_id":88001}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.failed_videos").value(0))
            .andExpect(jsonPath("$.data.logs[2]").value("同步只刷新元数据，真正花钱的步骤是右侧手动开始处理。"));

        Integer taskCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ingestion_tasks", Integer.class);
        org.assertj.core.api.Assertions.assertThat(taskCount).isZero();

        mockMvc.perform(get("/api/folders/{folderId}/videos", folderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cached").value(true))
            .andExpect(jsonPath("$.data.stale").value(false))
            .andExpect(jsonPath("$.data.videos[0].bvid").value(bvid))
            .andExpect(jsonPath("$.data.videos[0].title").value("Spring AI Alibaba 实战"));

        transcriptMapper.insert(Transcript.builder()
            .bvid(bvid)
            .sourceModel("paraformer-v2")
            .segmentCount(3)
            .transcriptText(transcriptText)
            .segmentsJson("[]")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());

        mockMvc.perform(post("/api/videos/{bvid}/summary", bvid))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.available").value(true))
            .andExpect(jsonPath("$.data.generated").value(true))
            .andExpect(jsonPath("$.data.up_to_date").value(true));

        mockMvc.perform(get("/api/videos/{bvid}/summary", bvid))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.available").value(true))
            .andExpect(jsonPath("$.data.generated").value(false))
            .andExpect(jsonPath("$.data.up_to_date").value(true))
            .andExpect(jsonPath("$.data.summary_text").value("这期视频主要讲了如何用 Spring AI Alibaba 组织聊天、RAG 和 agent 能力。"));
    }
}
