package com.bin.bilibrain.catalog;

import com.bin.bilibrain.auth.AuthService;
import com.bin.bilibrain.auth.AuthSessionResponse;
import com.bin.bilibrain.entity.Folder;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.mapper.FolderMapper;
import com.bin.bilibrain.mapper.VideoMapper;
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

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CatalogControllerTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FolderMapper folderMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private AuthService authService;

    @BeforeEach
    void setUp() {
        reset(authService);
        when(authService.getSession()).thenReturn(new AuthSessionResponse(false, null, null));
    }

    @Test
    void listFoldersRequiresUidOrLoginWhenUnavailable() throws Exception {
        mockMvc.perform(get("/api/folders"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listFoldersReturnsPersistedFolders() throws Exception {
        folderMapper.insert(Folder.builder()
            .folderId(1001L)
            .uid(9527L)
            .title("Java AI")
            .mediaCount(12)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());

        videoMapper.insert(Video.builder()
            .bvid("BV1abc411111")
            .folderId(1001L)
            .title("Spring AI Alibaba 入门")
            .upName("BinCode")
            .duration(620)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .isInvalid(0)
            .build());

        mockMvc.perform(get("/api/folders").queryParam("uid", "9527"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folders[0].folder_id").value(1001))
            .andExpect(jsonPath("$.folders[0].title").value("Java AI"))
            .andExpect(jsonPath("$.folders[0].media_count").value(12))
            .andExpect(jsonPath("$.stats.folder_count").value(1))
            .andExpect(jsonPath("$.stats.video_count").value(1))
            .andExpect(jsonPath("$.cached").value(true));
    }

    @Test
    void listFoldersFallsBackToLoggedInUid() throws Exception {
        folderMapper.insert(Folder.builder()
            .folderId(1001L)
            .uid(9527L)
            .title("Java AI")
            .mediaCount(12)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());
        folderMapper.insert(Folder.builder()
            .folderId(2002L)
            .uid(10086L)
            .title("Other")
            .mediaCount(9)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());

        when(authService.getSession()).thenReturn(new AuthSessionResponse(true, "BinCode", 9527L));

        mockMvc.perform(get("/api/folders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folders.length()").value(1))
            .andExpect(jsonPath("$.folders[0].folder_id").value(1001))
            .andExpect(jsonPath("$.folders[0].title").value("Java AI"));
    }

    @Test
    void listFolderVideosReturnsMappedVideoPayload() throws Exception {
        folderMapper.insert(Folder.builder()
            .folderId(2002L)
            .uid(9527L)
            .title("BiliBrain")
            .mediaCount(1)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());

        videoMapper.insert(Video.builder()
            .bvid("BV1xy4111111")
            .folderId(2002L)
            .title("BiliBrain Java 重构")
            .upName("BinCode")
            .coverUrl("https://example.com/cover.jpg")
            .duration(900)
            .publishedAt(LocalDateTime.now().minusDays(1))
            .syncedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .isInvalid(0)
            .build());

        mockMvc.perform(get("/api/folders/2002/videos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folder.folder_id").value(2002))
            .andExpect(jsonPath("$.fields[0]").value("bvid"))
            .andExpect(jsonPath("$.videos[0].bvid").value("BV1xy4111111"))
            .andExpect(jsonPath("$.videos[0].title").value("BiliBrain Java 重构"))
            .andExpect(jsonPath("$.videos[0].up_name").value("BinCode"))
            .andExpect(jsonPath("$.videos[0].sync_status").value("indexed"))
            .andExpect(jsonPath("$.videos[0].has_summary").value(false))
            .andExpect(jsonPath("$.videos[0].pipeline.audio.status").value("pending"))
            .andExpect(jsonPath("$.cached").value(true))
            .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    void listFolderVideosReturnsNotFoundForUnknownFolder() throws Exception {
        mockMvc.perform(get("/api/folders/999/videos"))
            .andExpect(status().isNotFound());
    }

    @TestConfiguration
    static class CatalogControllerTestConfig {
        @Bean
        @Primary
        AuthService authService() {
            return mock(AuthService.class);
        }
    }
}
