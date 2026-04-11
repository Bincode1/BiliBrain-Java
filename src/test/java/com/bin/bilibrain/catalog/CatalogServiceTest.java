package com.bin.bilibrain.catalog;

import com.bin.bilibrain.auth.AuthService;
import com.bin.bilibrain.auth.AuthSessionResponse;
import com.bin.bilibrain.bilibili.BilibiliFolderMetadata;
import com.bin.bilibrain.bilibili.BilibiliMetadataClient;
import com.bin.bilibrain.bilibili.BilibiliVideoMetadata;
import com.bin.bilibrain.entity.Folder;
import com.bin.bilibrain.entity.Video;
import com.bin.bilibrain.mapper.FolderMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.state.AppStateEntity;
import com.bin.bilibrain.state.AppStateMapper;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CatalogServiceTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private AppStateMapper appStateMapper;

    @Autowired
    private FolderMapper folderMapper;

    @Autowired
    private VideoMapper videoMapper;

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
    void listFoldersRefreshesSynchronouslyWhenCacheMissing() {
        when(bilibiliMetadataClient.listFolders(eq(9527L))).thenReturn(List.of(
            new BilibiliFolderMetadata(1001L, "Java AI", 12)
        ));

        FolderListResponse response = catalogService.listFolders(9527L);

        assertThat(response.cached()).isFalse();
        assertThat(response.stale()).isFalse();
        assertThat(response.folders()).hasSize(1);
        assertThat(response.folders().get(0).title()).isEqualTo("Java AI");
        assertThat(appStateMapper.selectById("cache:folders:9527")).isNotNull();
    }

    @Test
    void listFoldersReturnsStaleCacheAndSchedulesBackgroundRefresh() {
        LocalDateTime staleTime = LocalDateTime.now().minusMinutes(10);
        appStateMapper.insert(AppStateEntity.builder()
            .stateKey("cache:folders:9527")
            .stateValue("{\"uid\":9527}")
            .updatedAt(staleTime)
            .build());

        when(bilibiliMetadataClient.listFolders(eq(9527L))).thenReturn(List.of(
            new BilibiliFolderMetadata(1001L, "Refreshed Folder", 3)
        ));

        FolderListResponse response = catalogService.listFolders(9527L);

        assertThat(response.cached()).isTrue();
        assertThat(response.stale()).isTrue();
        verify(bilibiliMetadataClient, timeout(2000)).listFolders(9527L);
    }

    @Test
    void listFolderVideosRefreshesSynchronouslyWhenCacheMissing() {
        insertFolder(2002L, "BiliBrain");
        when(bilibiliMetadataClient.listFolderVideos(eq(2002L))).thenReturn(List.of(
            new BilibiliVideoMetadata(
                "BV1cache11111",
                "Cache Miss Video",
                "BinCode",
                "https://example.com/cache.jpg",
                600,
                false,
                LocalDateTime.now().minusDays(1)
            )
        ));

        FolderVideosResponse response = catalogService.listFolderVideos(2002L);

        assertThat(response.cached()).isFalse();
        assertThat(response.stale()).isFalse();
        assertThat(response.videos()).hasSize(1);
        assertThat(response.videos().get(0).title()).isEqualTo("Cache Miss Video");
        assertThat(appStateMapper.selectById("cache:folder-videos:2002")).isNotNull();
    }

    @Test
    void listFolderVideosReturnsStaleCacheAndSchedulesBackgroundRefresh() {
        insertFolder(2002L, "BiliBrain");
        insertVideo("BV1stale11111", 2002L, "Old Title");
        appStateMapper.insert(AppStateEntity.builder()
            .stateKey("cache:folder-videos:2002")
            .stateValue("{\"folder_id\":2002}")
            .updatedAt(LocalDateTime.now().minusMinutes(10))
            .build());

        when(bilibiliMetadataClient.listFolderVideos(eq(2002L))).thenReturn(List.of(
            new BilibiliVideoMetadata(
                "BV1stale11111",
                "Fresh Title",
                "BinCode",
                "https://example.com/fresh.jpg",
                720,
                false,
                LocalDateTime.now().minusDays(1)
            )
        ));

        FolderVideosResponse response = catalogService.listFolderVideos(2002L);

        assertThat(response.cached()).isTrue();
        assertThat(response.stale()).isTrue();
        assertThat(response.videos()).hasSize(1);
        verify(bilibiliMetadataClient, timeout(2000)).listFolderVideos(2002L);
    }

    private void insertFolder(long folderId, String title) {
        folderMapper.insert(Folder.builder()
            .folderId(folderId)
            .uid(9527L)
            .title(title)
            .mediaCount(1)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());
    }

    private void insertVideo(String bvid, long folderId, String title) {
        videoMapper.insert(Video.builder()
            .bvid(bvid)
            .folderId(folderId)
            .title(title)
            .upName("BinCode")
            .duration(300)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .isInvalid(0)
            .build());
    }

    @TestConfiguration
    static class CatalogServiceTestConfig {
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
