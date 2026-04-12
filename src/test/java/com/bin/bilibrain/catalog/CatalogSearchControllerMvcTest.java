package com.bin.bilibrain.catalog;

import com.bin.bilibrain.controller.CatalogController;
import com.bin.bilibrain.exception.GlobalExceptionHandler;
import com.bin.bilibrain.model.vo.catalog.BiliSearchVideoItemVO;
import com.bin.bilibrain.model.vo.catalog.FolderBiliSearchResponse;
import com.bin.bilibrain.model.vo.catalog.FolderSummaryResponse;
import com.bin.bilibrain.service.catalog.CatalogService;
import com.bin.bilibrain.service.catalog.CatalogSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogController.class)
@Import(GlobalExceptionHandler.class)
class CatalogSearchControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private CatalogSyncService catalogSyncService;

    @Test
    void searchEndpointReturnsBiliSearchPayload() throws Exception {
        when(catalogService.searchBiliVideosForFolder(3003L, null, 1, 12)).thenReturn(
            new FolderBiliSearchResponse(
                new FolderSummaryResponse(3003L, "Spring AI Alibaba", 8, "2026-04-12T12:00:00"),
                "Spring AI Alibaba",
                1,
                12,
                1,
                List.of(new BiliSearchVideoItemVO(
                    "BV1search0001",
                    "Spring AI Alibaba 实战",
                    "BinCode",
                    "搜索描述",
                    "https://example.com/search.jpg",
                    "12:30",
                    1024,
                    88,
                    "Spring AI",
                    "2026-04-12T10:00:00",
                    "https://www.bilibili.com/video/BV1search0001/"
                ))
            )
        );

        mockMvc.perform(get("/api/folders/3003/bili-search"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.keyword").value("Spring AI Alibaba"))
            .andExpect(jsonPath("$.data.results[0].watch_url").value("https://www.bilibili.com/video/BV1search0001/"));
    }
}
