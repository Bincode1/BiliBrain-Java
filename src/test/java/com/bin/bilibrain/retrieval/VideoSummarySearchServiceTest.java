package com.bin.bilibrain.retrieval;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.mapper.VideoSummaryMapper;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.entity.VideoSummary;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.service.retrieval.VideoSummarySearchService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoSummarySearchServiceTest {

    @Test
    void folderSummaryQueryReturnsAllScopeSummariesInsteadOfHardFilteringWholeSentence() {
        VideoSummaryMapper videoSummaryMapper = mock(VideoSummaryMapper.class);
        VideoMapper videoMapper = mock(VideoMapper.class);
        AppProperties appProperties = new AppProperties();
        appProperties.getRetrieval().setSearchTopK(5);

        when(videoMapper.selectList(any())).thenReturn(List.of(
            Video.builder().bvid("BV1sum001").folderId(2002L).build(),
            Video.builder().bvid("BV1sum002").folderId(2002L).build()
        ));
        when(videoSummaryMapper.selectBatchIds(any())).thenReturn(List.of(
            VideoSummary.builder().bvid("BV1sum001").summaryText("第一条视频主要讲 Spring AI Alibaba 的 Agent 架构。").updatedAt(LocalDateTime.now()).build(),
            VideoSummary.builder().bvid("BV1sum002").summaryText("第二条视频主要讲 RAG、向量检索和总结生成。").updatedAt(LocalDateTime.now().minusMinutes(1)).build()
        ));
        when(videoMapper.selectBatchIds(any())).thenReturn(List.of(
            Video.builder().bvid("BV1sum001").folderId(2002L).title("Agent 架构").upName("BinCode").build(),
            Video.builder().bvid("BV1sum002").folderId(2002L).title("RAG 总结").upName("BinCode").build()
        ));

        VideoSummarySearchService service = new VideoSummarySearchService(videoSummaryMapper, videoMapper, appProperties);

        List<ChatSourceVO> result = service.searchVideoSummaries("总结一下收藏夹", 2002L, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ChatSourceVO::bvid).containsExactlyInAnyOrder("BV1sum001", "BV1sum002");
        assertThat(result).allMatch(item -> item.folderId() != null && item.folderId().equals(2002L));
    }

    @Test
    void specificQueryStillRanksMatchingSummaryAheadWithoutDroppingScopeResults() {
        VideoSummaryMapper videoSummaryMapper = mock(VideoSummaryMapper.class);
        VideoMapper videoMapper = mock(VideoMapper.class);
        AppProperties appProperties = new AppProperties();
        appProperties.getRetrieval().setSearchTopK(2);

        when(videoMapper.selectList(any())).thenReturn(List.of(
            Video.builder().bvid("BV1sum101").folderId(3003L).build(),
            Video.builder().bvid("BV1sum102").folderId(3003L).build()
        ));
        when(videoSummaryMapper.selectBatchIds(any())).thenReturn(List.of(
            VideoSummary.builder().bvid("BV1sum101").summaryText("这期主要讲 Java Agent 和多工具编排。").updatedAt(LocalDateTime.now()).build(),
            VideoSummary.builder().bvid("BV1sum102").summaryText("这期主要讲前端布局和样式系统。").updatedAt(LocalDateTime.now().minusMinutes(1)).build()
        ));
        when(videoMapper.selectBatchIds(any())).thenReturn(List.of(
            Video.builder().bvid("BV1sum101").folderId(3003L).title("Java Agent 深入").upName("BinCode").build(),
            Video.builder().bvid("BV1sum102").folderId(3003L).title("前端排版").upName("BinCode").build()
        ));

        VideoSummarySearchService service = new VideoSummarySearchService(videoSummaryMapper, videoMapper, appProperties);

        List<ChatSourceVO> result = service.searchVideoSummaries("Java Agent 讲了什么", 3003L, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).bvid()).isEqualTo("BV1sum101");
    }
}
