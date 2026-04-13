package com.bin.bilibrain.agent;

import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.model.vo.tools.ToolCallResultVO;
import com.bin.bilibrain.service.agent.UnifiedAgentToolBridge;
import com.bin.bilibrain.service.retrieval.KnowledgeBaseSearchService;
import com.bin.bilibrain.service.retrieval.VideoSummarySearchService;
import com.bin.bilibrain.service.tools.ToolService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnifiedAgentToolBridgeTest {

    @Test
    void readSkillRecordsSkillAndToolEvents() {
        ToolService toolService = mock(ToolService.class);
        KnowledgeBaseSearchService knowledgeBaseSearchService = mock(KnowledgeBaseSearchService.class);
        VideoSummarySearchService videoSummarySearchService = mock(VideoSummarySearchService.class);
        when(toolService.callTool(any())).thenReturn(
            new ToolCallResultVO(1L, "read_skill", "SUCCESS", Map.of(
                "name", "java-rag",
                "description", "负责 Java RAG 问答",
                "content", "技能正文"
            ), "2026-04-12T13:30:00")
        );

        UnifiedAgentToolBridge bridge = new UnifiedAgentToolBridge(
            toolService,
            knowledgeBaseSearchService,
            videoSummarySearchService,
            null,
            null
        );

        Map<String, Object> result = bridge.readSkill("java-rag");

        assertThat(result).containsEntry("name", "java-rag");
        assertThat(bridge.skillEvents()).hasSize(1);
        assertThat(bridge.toolEvents()).hasSize(2);
        assertThat(bridge.toolEvents().get(0).name()).isEqualTo("read_skill");
        assertThat(bridge.toolEvents().get(0).phase()).isEqualTo("start");
        assertThat(bridge.toolEvents().get(1).phase()).isEqualTo("finish");
    }

    @Test
    void searchKnowledgeBaseCollectsSources() {
        ToolService toolService = mock(ToolService.class);
        KnowledgeBaseSearchService knowledgeBaseSearchService = mock(KnowledgeBaseSearchService.class);
        VideoSummarySearchService videoSummarySearchService = mock(VideoSummarySearchService.class);
        when(knowledgeBaseSearchService.searchKnowledgeBase("RAG 细节", 2002L, "BV1kb111")).thenReturn(
            List.of(new ChatSourceVO("chunk", "BV1kb111", 2002L, "RAG 视频", "BinCode", 12.0, 36.0, "这里讲了 RAG 细节"))
        );

        UnifiedAgentToolBridge bridge = new UnifiedAgentToolBridge(
            toolService,
            knowledgeBaseSearchService,
            videoSummarySearchService,
            2002L,
            "BV1kb111"
        );

        Map<String, Object> result = bridge.searchKnowledgeBase("RAG 细节");

        assertThat(result).containsEntry("count", 1);
        assertThat(bridge.collectedSources()).hasSize(1);
        assertThat(bridge.toolEvents()).hasSize(2);
        assertThat(bridge.toolEvents().get(0).name()).isEqualTo("search_knowledge_base");
        assertThat(bridge.toolEvents().get(0).phase()).isEqualTo("start");
        assertThat(bridge.toolEvents().get(1).phase()).isEqualTo("finish");
    }
}
