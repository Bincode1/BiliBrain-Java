package com.bin.bilibrain.catalog;

import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class VideoContentControllerTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private TranscriptMapper transcriptMapper;

    @Test
    void getTranscriptReturnsStoredTranscriptPayload() throws Exception {
        videoMapper.insert(Video.builder()
            .bvid("BV1transcript01")
            .folderId(2002L)
            .title("转写查看")
            .upName("BinCode")
            .duration(600)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .isInvalid(0)
            .build());
        transcriptMapper.insert(Transcript.builder()
            .bvid("BV1transcript01")
            .sourceModel("paraformer-v2")
            .segmentCount(3)
            .transcriptText("第一段\n第二段\n第三段")
            .segmentsJson("[]")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.of(2026, 4, 12, 13, 0))
            .build());

        mockMvc.perform(get("/api/videos/BV1transcript01/transcript"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.transcript_source").value("paraformer-v2"))
            .andExpect(jsonPath("$.data.segment_count").value(3))
            .andExpect(jsonPath("$.data.text").value("第一段\n第二段\n第三段"));
    }

    @Test
    void updateTagsPersistsNormalizedManualTags() throws Exception {
        videoMapper.insert(Video.builder()
            .bvid("BV1tags00001")
            .folderId(2002L)
            .title("标签保存")
            .upName("BinCode")
            .duration(480)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .isInvalid(0)
            .build());

        mockMvc.perform(post("/api/videos/BV1tags00001/tags")
                .contentType("application/json")
                .content("""
                    {"tags":[" Spring AI ","Java Agent","Spring AI"]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.bvid").value("BV1tags00001"))
            .andExpect(jsonPath("$.data.manual_tags[0]").value("Spring AI"))
            .andExpect(jsonPath("$.data.manual_tags[1]").value("Java Agent"));
    }
}
