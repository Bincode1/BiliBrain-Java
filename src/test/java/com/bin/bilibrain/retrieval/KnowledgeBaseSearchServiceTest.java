package com.bin.bilibrain.retrieval;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.service.retrieval.KnowledgeBaseSearchService;
import com.bin.bilibrain.service.retrieval.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseSearchServiceTest {

    @Test
    void mapsVectorDocumentsToChunkSources() {
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        AppProperties appProperties = new AppProperties();
        appProperties.getRetrieval().setSearchTopK(3);

        when(vectorSearchService.isAvailable()).thenReturn(true);
        when(vectorSearchService.similaritySearch("第几分钟讲了什么", 3, 2002L, "BV1chunk111"))
            .thenReturn(List.of(new Document(
                "BV1chunk111#chunk-0",
                "这里是一段转写片段内容，用来回答问题。",
                Map.of(
                    "bvid", "BV1chunk111",
                    "folder_id", 2002L,
                    "video_title", "Spring AI Alibaba 实战",
                    "up_name", "BinCode",
                    "start_seconds", 61.5,
                    "end_seconds", 95.0
                )
            )));

        KnowledgeBaseSearchService service = new KnowledgeBaseSearchService(vectorSearchService, appProperties);

        List<ChatSourceVO> result = service.searchKnowledgeBase("第几分钟讲了什么", 2002L, "BV1chunk111");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceType()).isEqualTo("chunk");
        assertThat(result.get(0).bvid()).isEqualTo("BV1chunk111");
        assertThat(result.get(0).folderId()).isEqualTo(2002L);
        assertThat(result.get(0).startSeconds()).isEqualTo(61.5);
        assertThat(result.get(0).endSeconds()).isEqualTo(95.0);
        assertThat(result.get(0).excerpt()).contains("转写片段内容");
    }

    @Test
    void returnsEmptyWhenVectorStoreIsUnavailable() {
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        AppProperties appProperties = new AppProperties();
        KnowledgeBaseSearchService service = new KnowledgeBaseSearchService(vectorSearchService, appProperties);

        when(vectorSearchService.isAvailable()).thenReturn(false);

        List<ChatSourceVO> result = service.searchKnowledgeBase("测试", null, null);

        assertThat(result).isEmpty();
        verify(vectorSearchService).isAvailable();
    }
}
