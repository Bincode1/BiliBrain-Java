package com.bin.bilibrain.service.agent;

import com.bin.bilibrain.model.dto.tools.ToolCallRequest;
import com.bin.bilibrain.model.vo.agent.AgentSkillEventVO;
import com.bin.bilibrain.model.vo.agent.AgentToolEventVO;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.model.vo.tools.ToolCallResultVO;
import com.bin.bilibrain.service.retrieval.KnowledgeBaseSearchService;
import com.bin.bilibrain.service.retrieval.VideoSummarySearchService;
import com.bin.bilibrain.service.tools.ToolService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UnifiedAgentToolBridge {
    private final ToolService toolService;
    private final KnowledgeBaseSearchService knowledgeBaseSearchService;
    private final VideoSummarySearchService videoSummarySearchService;
    private final AgentScopeService agentScopeService;
    private final Long folderId;
    private final String videoBvid;
    private final List<AgentToolEventVO> toolEvents = new ArrayList<>();
    private final List<AgentSkillEventVO> skillEvents = new ArrayList<>();
    private final List<ChatSourceVO> collectedSources = new ArrayList<>();

    public UnifiedAgentToolBridge(
        ToolService toolService,
        KnowledgeBaseSearchService knowledgeBaseSearchService,
        VideoSummarySearchService videoSummarySearchService,
        AgentScopeService agentScopeService,
        Long folderId,
        String videoBvid
    ) {
        this.toolService = toolService;
        this.knowledgeBaseSearchService = knowledgeBaseSearchService;
        this.videoSummarySearchService = videoSummarySearchService;
        this.agentScopeService = agentScopeService;
        this.folderId = folderId;
        this.videoBvid = videoBvid;
    }

    @Tool(name = ToolService.TOOL_READ_SKILL, description = "读取指定 skill 的描述与正文")
    public Map<String, Object> readSkill(
        @ToolParam(description = "skill 的名称") String skillName
    ) {
        Map<String, Object> summary = Map.of("skill_name", skillName);
        toolEvents.add(AgentToolEventVO.start(ToolService.TOOL_READ_SKILL, summary));
        try {
            ToolCallResultVO result = toolService.callTool(new ToolCallRequest(ToolService.TOOL_READ_SKILL, null, skillName));
            skillEvents.add(new AgentSkillEventVO(
                String.valueOf(result.result().getOrDefault("name", skillName)),
                String.valueOf(result.result().getOrDefault("description", ""))
            ));
            toolEvents.add(AgentToolEventVO.finish(
                ToolService.TOOL_READ_SKILL,
                summary,
                Map.of(
                    "name", String.valueOf(result.result().getOrDefault("name", skillName)),
                    "active", result.result().getOrDefault("active", false)
                )
            ));
            return result.result();
        } catch (RuntimeException exception) {
            toolEvents.add(AgentToolEventVO.failed(ToolService.TOOL_READ_SKILL, summary, exception.getMessage()));
            throw exception;
        }
    }

    @Tool(name = "search_knowledge_base", description = "搜索 transcript chunk 级知识库片段")
    public Map<String, Object> searchKnowledgeBase(
        @ToolParam(description = "用户的当前问题") String query
    ) {
        Map<String, Object> summary = Map.of("query", query);
        toolEvents.add(AgentToolEventVO.start("search_knowledge_base", summary));
        try {
            List<ChatSourceVO> sources = knowledgeBaseSearchService.searchKnowledgeBase(query, folderId, videoBvid);
            if (sources.isEmpty()) {
                String message = agentScopeService.emptyContextMessage("", folderId, videoBvid);
                toolEvents.add(AgentToolEventVO.finish(
                    "search_knowledge_base",
                    summary,
                    Map.of("count", 0, "message", message)
                ));
                return Map.of("count", 0, "sources", List.of(), "message", message);
            }
            collectedSources.addAll(sources);
            toolEvents.add(AgentToolEventVO.finish(
                "search_knowledge_base",
                summary,
                Map.of("count", sources.size())
            ));
            return Map.of("count", sources.size(), "sources", sources);
        } catch (RuntimeException exception) {
            toolEvents.add(AgentToolEventVO.failed("search_knowledge_base", summary, exception.getMessage()));
            throw exception;
        }
    }

    @Tool(name = "search_video_summaries", description = "搜索视频摘要级上下文")
    public Map<String, Object> searchVideoSummaries(
        @ToolParam(description = "用户的当前问题") String query
    ) {
        Map<String, Object> summary = Map.of("query", query);
        toolEvents.add(AgentToolEventVO.start("search_video_summaries", summary));
        try {
            List<ChatSourceVO> sources = videoSummarySearchService.searchVideoSummaries(query, folderId, videoBvid);
            if (sources.isEmpty()) {
                String message = agentScopeService.emptyContextMessage("", folderId, videoBvid);
                toolEvents.add(AgentToolEventVO.finish(
                    "search_video_summaries",
                    summary,
                    Map.of("count", 0, "message", message)
                ));
                return Map.of("count", 0, "sources", List.of(), "message", message);
            }
            collectedSources.addAll(sources);
            toolEvents.add(AgentToolEventVO.finish(
                "search_video_summaries",
                summary,
                Map.of("count", sources.size())
            ));
            return Map.of("count", sources.size(), "sources", sources);
        } catch (RuntimeException exception) {
            toolEvents.add(AgentToolEventVO.failed("search_video_summaries", summary, exception.getMessage()));
            throw exception;
        }
    }

    public List<AgentToolEventVO> toolEvents() {
        return List.copyOf(toolEvents);
    }

    public List<AgentSkillEventVO> skillEvents() {
        return List.copyOf(skillEvents);
    }

    public List<ChatSourceVO> collectedSources() {
        return List.copyOf(collectedSources);
    }
}
