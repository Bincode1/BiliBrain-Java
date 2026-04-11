package com.bin.bilibrain.catalog;

import com.bin.bilibrain.auth.AuthService;
import com.bin.bilibrain.auth.AuthSessionResponse;
import com.bin.bilibrain.bilibili.BilibiliFolderMetadata;
import com.bin.bilibrain.bilibili.BilibiliMetadataClient;
import com.bin.bilibrain.bilibili.BilibiliVideoMetadata;
import com.bin.bilibrain.entity.Folder;
import com.bin.bilibrain.mapper.FolderMapper;
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
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CatalogSyncControllerTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FolderMapper folderMapper;

    @Autowired
    private BilibiliMetadataClient bilibiliMetadataClient;

    @Autowired
    private AuthService authService;

    @BeforeEach
    void setUp() {
        reset(bilibiliMetadataClient, authService);
        when(authService.getSession()).thenReturn(new AuthSessionResponse(false, null, null));
    }

    @Test
    void syncFoldersPersistsRemoteFolders() throws Exception {
        when(bilibiliMetadataClient.listFolders(eq(9527L))).thenReturn(List.of(
            new BilibiliFolderMetadata(1001L, "Java AI", 12),
            new BilibiliFolderMetadata(1002L, "BiliBrain", 8)
        ));

        mockMvc.perform(post("/api/folders/sync")
                .contentType("application/json")
                .content("""
                    {"uid":9527}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uid").value(9527))
            .andExpect(jsonPath("$.new_folders").value(2))
            .andExpect(jsonPath("$.updated_folders").value(0))
            .andExpect(jsonPath("$.stats.folder_count").value(2));

        mockMvc.perform(get("/api/folders").queryParam("uid", "9527"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folders[0].folder_id").value(1001))
            .andExpect(jsonPath("$.folders[1].folder_id").value(1002));
    }

    @Test
    void syncFoldersFallsBackToLoggedInUid() throws Exception {
        when(authService.getSession()).thenReturn(new AuthSessionResponse(true, "BinCode", 9527L));
        when(bilibiliMetadataClient.listFolders(eq(9527L))).thenReturn(List.of(
            new BilibiliFolderMetadata(1001L, "Java AI", 12)
        ));

        mockMvc.perform(post("/api/folders/sync")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uid").value(9527))
            .andExpect(jsonPath("$.new_folders").value(1));
    }

    @Test
    void syncFolderVideosPersistsRemoteVideos() throws Exception {
        folderMapper.insert(Folder.builder()
            .folderId(2002L)
            .uid(9527L)
            .title("BiliBrain")
            .mediaCount(2)
            .createdAt(LocalDateTime.of(2026, 4, 11, 10, 0))
            .updatedAt(LocalDateTime.of(2026, 4, 11, 10, 0))
            .build());

        when(bilibiliMetadataClient.listFolderVideos(eq(2002L))).thenReturn(List.of(
            new BilibiliVideoMetadata(
                "BV1abc411111",
                "Spring AI Alibaba 入门",
                "BinCode",
                "https://example.com/a.jpg",
                620,
                false,
                LocalDateTime.of(2026, 4, 10, 8, 0)
            ),
            new BilibiliVideoMetadata(
                "invalid:2002:page1-idx1",
                "已失效视频",
                "视频已失效",
                "",
                0,
                true,
                null
            )
        ));

        mockMvc.perform(post("/api/sync")
                .contentType("application/json")
                .content("""
                    {"folder_id":2002}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folder").value("BiliBrain"))
            .andExpect(jsonPath("$.failed_videos").value(0))
            .andExpect(jsonPath("$.stats.video_count").value(2));

        mockMvc.perform(get("/api/folders/2002/videos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.videos[*].bvid", containsInAnyOrder("BV1abc411111", "invalid:2002:page1-idx1")))
            .andExpect(jsonPath("$.videos[*].sync_status", hasItem("pending")))
            .andExpect(jsonPath("$.videos[*].is_invalid", hasItem(true)));
    }

    @Test
    void syncFoldersRequiresUidOrLoginWhenUnavailable() throws Exception {
        mockMvc.perform(post("/api/folders/sync")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class CatalogSyncControllerTestConfig {
        @Bean
        @Primary
        BilibiliMetadataClient bilibiliMetadataClient() {
            return mock(BilibiliMetadataClient.class);
        }

        @Bean
        @Primary
        AuthService authService() {
            return mock(AuthService.class);
        }
    }
}
