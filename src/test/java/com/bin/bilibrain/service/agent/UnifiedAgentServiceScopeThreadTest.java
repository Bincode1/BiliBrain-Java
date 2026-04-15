package com.bin.bilibrain.service.agent;

import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.service.retrieval.KnowledgeBaseSearchService;
import com.bin.bilibrain.service.retrieval.VideoSummarySearchService;
import com.bin.bilibrain.service.tools.ToolService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnifiedAgentServiceScopeThreadTest {

    @Test
    void buildAgentThreadIdChangesWhenScopeChanges() {
        AgentScopeService agentScopeService = mock(AgentScopeService.class);
        ToolService toolService = mock(ToolService.class);
        KnowledgeBaseSearchService knowledgeBaseSearchService = mock(KnowledgeBaseSearchService.class);
        VideoSummarySearchService videoSummarySearchService = mock(VideoSummarySearchService.class);
        VideoMapper videoMapper = mock(VideoMapper.class);
        when(agentScopeService.resolveScope("global", null, null))
            .thenReturn(new AgentScopeService.ScopeSelection("global", null, null));
        when(agentScopeService.resolveScope("video", 123L, "BV1test999"))
            .thenReturn(new AgentScopeService.ScopeSelection("video", 123L, "BV1test999"));

        UnifiedAgentToolBridge globalBridge = new UnifiedAgentToolBridge(
            toolService,
            knowledgeBaseSearchService,
            videoSummarySearchService,
            agentScopeService,
            videoMapper,
            "global",
            null,
            null
        );
        UnifiedAgentToolBridge videoBridge = new UnifiedAgentToolBridge(
            toolService,
            knowledgeBaseSearchService,
            videoSummarySearchService,
            agentScopeService,
            videoMapper,
            "video",
            123L,
            "BV1test999"
        );

        assertThat(UnifiedAgentService.buildAgentThreadId("conv-1", globalBridge))
            .isEqualTo("conv-1::global");
        assertThat(UnifiedAgentService.buildAgentThreadId("conv-1", videoBridge))
            .isEqualTo("conv-1::video:BV1test999");
    }

    @Test
    void buildScopeVideoListAnswerUsesFixedMachineFriendlyFormat() {
        AgentScopeService agentScopeService = mock(AgentScopeService.class);
        ToolService toolService = mock(ToolService.class);
        KnowledgeBaseSearchService knowledgeBaseSearchService = mock(KnowledgeBaseSearchService.class);
        VideoSummarySearchService videoSummarySearchService = mock(VideoSummarySearchService.class);
        VideoMapper videoMapper = mock(VideoMapper.class);
        when(agentScopeService.resolveScope("global", null, null))
            .thenReturn(new AgentScopeService.ScopeSelection("global", null, null));
        when(videoMapper.selectList(any())).thenReturn(java.util.List.of(
            Video.builder().bvid("BV1A").title("Agent Skill 从使用到原理").upName("UP-A").folderId(1L).isInvalid(0).build(),
            Video.builder().bvid("BV1B").title("新乡旅行攻略").upName("UP-B").folderId(2L).isInvalid(0).build()
        ));

        UnifiedAgentToolBridge bridge = new UnifiedAgentToolBridge(
            toolService,
            knowledgeBaseSearchService,
            videoSummarySearchService,
            agentScopeService,
            videoMapper,
            "global",
            null,
            null
        );

        bridge.listScopeVideos();
        String answer = UnifiedAgentService.buildScopeVideoListAnswer(bridge);

        assertThat(answer).isEqualTo("""
            当前范围共 2 个视频：

            1. Agent Skill 从使用到原理 | UP: UP-A | BV: BV1A
            2. 新乡旅行攻略 | UP: UP-B | BV: BV1B""");
        assertThat(answer).doesNotContain("AI 与 Agent 技术类");
        assertThat(answer).doesNotContain("河南新乡旅游与胖东来攻略类");
    }
}
