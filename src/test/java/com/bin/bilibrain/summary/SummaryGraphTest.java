package com.bin.bilibrain.summary;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.graph.summary.GenerateDirectSummaryNode;
import com.bin.bilibrain.graph.summary.GenerateWindowSummariesNode;
import com.bin.bilibrain.graph.summary.LoadSummaryContextNode;
import com.bin.bilibrain.graph.summary.PrepareSummaryWindowsNode;
import com.bin.bilibrain.graph.summary.ReduceWindowSummariesNode;
import com.bin.bilibrain.graph.summary.SaveSummaryNode;
import com.bin.bilibrain.graph.summary.SummaryGraphConfig;
import com.bin.bilibrain.graph.summary.SummaryGraphRunner;
import com.bin.bilibrain.graph.summary.SummaryGraphStateStore;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.entity.VideoSummary;
import com.bin.bilibrain.service.summary.SummaryGenerationService;
import com.bin.bilibrain.service.summary.SummaryHashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryGraphTest {

    private SummaryGraphStateStore stateStore;
    private SummaryGenerationService summaryGenerationService;
    private SummaryGraphRunner summaryGraphRunner;

    @BeforeEach
    void setUp() throws Exception {
        stateStore = Mockito.mock(SummaryGraphStateStore.class);
        summaryGenerationService = Mockito.mock(SummaryGenerationService.class);

        AppProperties appProperties = new AppProperties();
        appProperties.getSummary().setDirectMaxCharacters(40);
        appProperties.getSummary().setWindowMaxCharacters(30);
        appProperties.getSummary().setWindowOverlapCharacters(5);

        StateGraph stateGraph = new SummaryGraphConfig().summaryStateGraph(
            new LoadSummaryContextNode(stateStore),
            new PrepareSummaryWindowsNode(appProperties),
            new GenerateDirectSummaryNode(summaryGenerationService),
            new GenerateWindowSummariesNode(summaryGenerationService),
            new ReduceWindowSummariesNode(summaryGenerationService),
            new SaveSummaryNode(stateStore)
        );
        CompiledGraph compiledGraph = new SummaryGraphConfig().summaryCompiledGraph(stateGraph);
        summaryGraphRunner = new SummaryGraphRunner(compiledGraph);
    }

    @Test
    void returnsCachedSummaryWhenTranscriptHashMatches() {
        Video video = video("BV1graphcache");
        Transcript transcript = transcript("BV1graphcache", "短文本摘要");
        String hash = SummaryHashUtils.sha256(transcript.getTranscriptText());
        VideoSummary existingSummary = VideoSummary.builder()
            .bvid("BV1graphcache")
            .transcriptHash(hash)
            .summaryText("已有缓存摘要")
            .updatedAt(LocalDateTime.now())
            .build();

        when(stateStore.requireVideo("BV1graphcache")).thenReturn(video);
        when(stateStore.requireTranscript("BV1graphcache")).thenReturn(transcript);
        when(stateStore.findSummary("BV1graphcache")).thenReturn(existingSummary);

        SummaryGraphRunner.SummaryRunResult result = summaryGraphRunner.run("BV1graphcache");

        assertThat(result.generated()).isFalse();
        assertThat(result.summary().getSummaryText()).isEqualTo("已有缓存摘要");
        verify(summaryGenerationService, never()).generateDirectSummary(any(), anyString());
        verify(summaryGenerationService, never()).generateWindowSummaries(any(), anyList());
        verify(stateStore, never()).saveSummary(anyString(), anyString(), anyString());
    }

    @Test
    void shortTranscriptUsesDirectSummaryPath() {
        Video video = video("BV1graphdirect");
        Transcript transcript = transcript("BV1graphdirect", "这是一个足够短的转写文本，用来测试 direct summary。");
        VideoSummary savedSummary = VideoSummary.builder()
            .bvid("BV1graphdirect")
            .transcriptHash(SummaryHashUtils.sha256(transcript.getTranscriptText()))
            .summaryText("直接摘要结果")
            .updatedAt(LocalDateTime.now())
            .build();

        when(stateStore.requireVideo("BV1graphdirect")).thenReturn(video);
        when(stateStore.requireTranscript("BV1graphdirect")).thenReturn(transcript);
        when(stateStore.findSummary("BV1graphdirect")).thenReturn(null);
        when(summaryGenerationService.generateDirectSummary(any(), anyString())).thenReturn("直接摘要结果");
        when(stateStore.saveSummary(anyString(), anyString(), anyString())).thenReturn(savedSummary);

        SummaryGraphRunner.SummaryRunResult result = summaryGraphRunner.run("BV1graphdirect");

        assertThat(result.generated()).isTrue();
        assertThat(result.summary().getSummaryText()).isEqualTo("直接摘要结果");
        verify(summaryGenerationService).generateDirectSummary(any(), anyString());
        verify(summaryGenerationService, never()).generateWindowSummaries(any(), anyList());
        verify(summaryGenerationService, never()).reduceWindowSummaries(any(), anyList());
    }

    @Test
    void longTranscriptUsesWindowedReducePath() {
        Video video = video("BV1graphwindow");
        Transcript transcript = transcript(
            "BV1graphwindow",
            "第一段非常长的转写内容，用来触发窗口拆分。" +
                "第二段继续补充更多信息，让文本长度超过 direct threshold。" +
                "第三段再补一部分内容，确保一定会进入 windowed summary。" +
                "第四段用于验证 reduce 节点会归并多个窗口摘要。"
        );
        VideoSummary savedSummary = VideoSummary.builder()
            .bvid("BV1graphwindow")
            .transcriptHash(SummaryHashUtils.sha256(transcript.getTranscriptText()))
            .summaryText("归并后的最终摘要")
            .updatedAt(LocalDateTime.now())
            .build();

        when(stateStore.requireVideo("BV1graphwindow")).thenReturn(video);
        when(stateStore.requireTranscript("BV1graphwindow")).thenReturn(transcript);
        when(stateStore.findSummary("BV1graphwindow")).thenReturn(null);
        when(summaryGenerationService.generateWindowSummaries(any(), anyList()))
            .thenReturn(List.of("窗口摘要一", "窗口摘要二"));
        when(summaryGenerationService.reduceWindowSummaries(any(), anyList()))
            .thenReturn("归并后的最终摘要");
        when(stateStore.saveSummary(anyString(), anyString(), anyString())).thenReturn(savedSummary);

        SummaryGraphRunner.SummaryRunResult result = summaryGraphRunner.run("BV1graphwindow");

        assertThat(result.generated()).isTrue();
        assertThat(result.summary().getSummaryText()).isEqualTo("归并后的最终摘要");
        verify(summaryGenerationService, never()).generateDirectSummary(any(), anyString());
        verify(summaryGenerationService).generateWindowSummaries(any(), anyList());
        verify(summaryGenerationService).reduceWindowSummaries(any(), anyList());
    }

    private Video video(String bvid) {
        return Video.builder()
            .bvid(bvid)
            .title("Summary Graph Test")
            .upName("BinCode")
            .build();
    }

    private Transcript transcript(String bvid, String text) {
        return Transcript.builder()
            .bvid(bvid)
            .transcriptText(text)
            .build();
    }
}
