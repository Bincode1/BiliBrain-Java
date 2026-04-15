package com.bin.bilibrain.service.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.model.entity.Video;
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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UnifiedAgentToolBridge {
    private final ToolService toolService;
    private final KnowledgeBaseSearchService knowledgeBaseSearchService;
    private final VideoSummarySearchService videoSummarySearchService;
    private final AgentScopeService agentScopeService;
    private final VideoMapper videoMapper;
    private final AgentScopeService.ScopeSelection scope;
    private final List<AgentToolEventVO> toolEvents = new ArrayList<>();
    private final List<AgentSkillEventVO> skillEvents = new ArrayList<>();
    private final List<ChatSourceVO> collectedSources = new ArrayList<>();
    private List<ScopeVideoItem> listedScopeVideos = List.of();

    public UnifiedAgentToolBridge(
        ToolService toolService,
        KnowledgeBaseSearchService knowledgeBaseSearchService,
        VideoSummarySearchService videoSummarySearchService,
        AgentScopeService agentScopeService,
        VideoMapper videoMapper,
        String scopeMode,
        Long folderId,
        String videoBvid
    ) {
        this.toolService = toolService;
        this.knowledgeBaseSearchService = knowledgeBaseSearchService;
        this.videoSummarySearchService = videoSummarySearchService;
        this.agentScopeService = agentScopeService;
        this.videoMapper = videoMapper;
        this.scope = agentScopeService.resolveScope(scopeMode, folderId, videoBvid);
    }

    @Tool(name = ToolService.TOOL_READ_SKILL, description = "读取指定 skill 的描述与正文")
    public Map<String, Object> readSkill(
        @ToolParam(description = "skill 的名称") String skillName
    ) {
        Map<String, Object> summary = Map.of("skill_name", skillName);
        toolEvents.add(AgentToolEventVO.start(ToolService.TOOL_READ_SKILL, summary));
        try {
            ToolCallResultVO result = toolService.callTool(new ToolCallRequest(ToolService.TOOL_READ_SKILL, null, skillName, null));
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
            List<ChatSourceVO> sources = knowledgeBaseSearchService.searchKnowledgeBase(query, folderId(), videoBvid());
            if (sources.isEmpty()) {
                String message = agentScopeService.emptyContextMessage(scopeMode(), folderId(), videoBvid());
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
            List<ChatSourceVO> sources = videoSummarySearchService.searchVideoSummaries(query, folderId(), videoBvid());
            if (sources.isEmpty()) {
                String message = agentScopeService.emptyContextMessage(scopeMode(), folderId(), videoBvid());
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

    @Tool(name = "list_scope_videos", description = "列出当前范围内的视频清单，适合回答“当前有哪些视频/能看到什么视频”")
    public Map<String, Object> listScopeVideos() {
        Map<String, Object> summary = Map.of("scope_mode", scopeMode());
        toolEvents.add(AgentToolEventVO.start("list_scope_videos", summary));
        try {
            listedScopeVideos = loadScopeVideos().stream()
                .map(video -> new ScopeVideoItem(
                    safe(video.getBvid()),
                    safe(video.getTitle()),
                    safe(video.getUpName()),
                    video.getFolderId()
                ))
                .toList();
            List<Map<String, Object>> videos = listedScopeVideos.stream()
                .map(video -> Map.<String, Object>of(
                    "bvid", video.bvid(),
                    "title", video.title(),
                    "up_name", video.upName(),
                    "folder_id", video.folderId() == null ? "" : video.folderId()
                ))
                .toList();
            toolEvents.add(AgentToolEventVO.finish(
                "list_scope_videos",
                summary,
                Map.of("count", videos.size())
            ));
            return Map.of(
                "count", videos.size(),
                "scope_mode", scopeMode(),
                "videos", videos
            );
        } catch (RuntimeException exception) {
            toolEvents.add(AgentToolEventVO.failed("list_scope_videos", summary, exception.getMessage()));
            throw exception;
        }
    }

    @Tool(name = ToolService.TOOL_PUBLISH_TO_VAULT_FS, description = "将整理好的 Markdown 内容发布到本地知识库或 Obsidian vault")
    public Map<String, Object> publishToVaultFs(
        @ToolParam(description = "发布类型，可选值：video_note、folder_guide、review_plan；如不确定可留空") String kind,
        @ToolParam(description = "文档标题") String title,
        @ToolParam(description = "要发布的 Markdown 内容") String contentMarkdown
    ) {
        Map<String, Object> summary = Map.of(
            "kind", resolvePublishKind(kind),
            "title", title == null ? "" : title
        );
        toolEvents.add(AgentToolEventVO.start(ToolService.TOOL_PUBLISH_TO_VAULT_FS, summary));
        try {
            ToolCallResultVO result = toolService.callTool(new ToolCallRequest(
                ToolService.TOOL_PUBLISH_TO_VAULT_FS,
                null,
                null,
                Map.of(
                    "kind", resolvePublishKind(kind),
                    "title", title == null ? "" : title,
                    "content_markdown", contentMarkdown == null ? "" : contentMarkdown,
                    "scope_type", resolveScopeType(),
                    "scope_id", resolveScopeId(),
                    "source_refs", collectedSources()
                )
            ));
            toolEvents.add(AgentToolEventVO.finish(
                ToolService.TOOL_PUBLISH_TO_VAULT_FS,
                summary,
                result.result()
            ));
            return result.result();
        } catch (RuntimeException exception) {
            toolEvents.add(AgentToolEventVO.failed(ToolService.TOOL_PUBLISH_TO_VAULT_FS, summary, exception.getMessage()));
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

    public boolean hasScopeVideoListing() {
        return !listedScopeVideos.isEmpty() || toolEvents.stream().anyMatch(event ->
            "list_scope_videos".equals(event.name()) && "finish".equals(event.phase())
        );
    }

    public List<ScopeVideoItem> listedScopeVideos() {
        return List.copyOf(listedScopeVideos);
    }

    public Long folderId() {
        return scope.folderId();
    }

    public String videoBvid() {
        return scope.videoBvid();
    }

    public String scopeMode() {
        return scope.scopeMode();
    }

    private String resolveScopeType() {
        if (StringUtils.hasText(videoBvid())) {
            return "video";
        }
        if (folderId() != null) {
            return "folder";
        }
        return "global";
    }

    private String resolveScopeId() {
        if (StringUtils.hasText(videoBvid())) {
            return videoBvid().trim();
        }
        if (folderId() != null) {
            return String.valueOf(folderId());
        }
        return "global";
    }

    private String resolvePublishKind(String kind) {
        if (StringUtils.hasText(kind)) {
            return kind.trim();
        }
        if (StringUtils.hasText(videoBvid())) {
            return "video_note";
        }
        if (folderId() != null) {
            return "folder_guide";
        }
        return "review_plan";
    }

    private List<Video> loadScopeVideos() {
        if (StringUtils.hasText(videoBvid())) {
            Video video = videoMapper.selectById(videoBvid());
            return video == null ? List.of() : List.of(video);
        }
        LambdaQueryWrapper<Video> queryWrapper = new LambdaQueryWrapper<Video>()
            .eq(folderId() != null, Video::getFolderId, folderId())
            .ne(Video::getIsInvalid, 1)
            .orderByDesc(Video::getCreatedAt)
            .orderByDesc(Video::getUpdatedAt);
        return videoMapper.selectList(queryWrapper);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record ScopeVideoItem(String bvid, String title, String upName, Long folderId) {
    }
}
