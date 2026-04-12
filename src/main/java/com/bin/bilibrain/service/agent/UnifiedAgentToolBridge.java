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
    private final Long folderId;
    private final String videoBvid;
    private final List<AgentToolEventVO> toolEvents = new ArrayList<>();
    private final List<AgentSkillEventVO> skillEvents = new ArrayList<>();
    private final List<ChatSourceVO> collectedSources = new ArrayList<>();

    public UnifiedAgentToolBridge(
        ToolService toolService,
        KnowledgeBaseSearchService knowledgeBaseSearchService,
        VideoSummarySearchService videoSummarySearchService,
        Long folderId,
        String videoBvid
    ) {
        this.toolService = toolService;
        this.knowledgeBaseSearchService = knowledgeBaseSearchService;
        this.videoSummarySearchService = videoSummarySearchService;
        this.folderId = folderId;
        this.videoBvid = videoBvid;
    }

    @Tool(name = ToolService.TOOL_READ_SKILL, description = "读取指定 skill 的描述与正文")
    public Map<String, Object> readSkill(
        @ToolParam(description = "skill 的名称") String skillName
    ) {
        ToolCallResultVO result = toolService.callTool(new ToolCallRequest(ToolService.TOOL_READ_SKILL, null, skillName));
        skillEvents.add(new AgentSkillEventVO(
            String.valueOf(result.result().getOrDefault("name", skillName)),
            String.valueOf(result.result().getOrDefault("description", ""))
        ));
        toolEvents.add(new AgentToolEventVO(ToolService.TOOL_READ_SKILL, "已读取 skill：" + skillName));
        return result.result();
    }

    @Tool(name = ToolService.TOOL_LIST_WORKSPACES, description = "列出当前可用的工作区")
    public Map<String, Object> listWorkspaces() {
        ToolCallResultVO result = toolService.callTool(new ToolCallRequest(ToolService.TOOL_LIST_WORKSPACES, null, null));
        toolEvents.add(new AgentToolEventVO(
            ToolService.TOOL_LIST_WORKSPACES,
            "已读取工作区列表，共 %s 个".formatted(result.result().getOrDefault("count", 0))
        ));
        return result.result();
    }

    @Tool(name = "search_knowledge_base", description = "搜索 transcript chunk 级知识库片段")
    public Map<String, Object> searchKnowledgeBase(
        @ToolParam(description = "用户的当前问题") String query
    ) {
        List<ChatSourceVO> sources = knowledgeBaseSearchService.searchKnowledgeBase(query, folderId, videoBvid);
        collectedSources.addAll(sources);
        toolEvents.add(new AgentToolEventVO("search_knowledge_base", "命中 %s 条 chunk 片段".formatted(sources.size())));
        return Map.of("count", sources.size(), "sources", sources);
    }

    @Tool(name = "search_video_summaries", description = "搜索视频摘要级上下文")
    public Map<String, Object> searchVideoSummaries(
        @ToolParam(description = "用户的当前问题") String query
    ) {
        List<ChatSourceVO> sources = videoSummarySearchService.searchVideoSummaries(query, folderId, videoBvid);
        collectedSources.addAll(sources);
        toolEvents.add(new AgentToolEventVO("search_video_summaries", "命中 %s 条摘要结果".formatted(sources.size())));
        return Map.of("count", sources.size(), "sources", sources);
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
