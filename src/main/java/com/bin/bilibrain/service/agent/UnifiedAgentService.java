package com.bin.bilibrain.service.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.bin.bilibrain.ai.client.DashScopeChatClientFactory;
import com.bin.bilibrain.ai.prompt.UnifiedAgentPrompts;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.model.dto.agent.AgentApprovalDecisionRequest;
import com.bin.bilibrain.model.dto.agent.AgentResumeStreamRequest;
import com.bin.bilibrain.model.vo.agent.AgentApprovalItemVO;
import com.bin.bilibrain.model.vo.agent.AgentApprovalVO;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.service.retrieval.KnowledgeBaseSearchService;
import com.bin.bilibrain.service.retrieval.VideoSummarySearchService;
import com.bin.bilibrain.service.skills.SkillRegistryService;
import com.bin.bilibrain.service.tools.ToolPolicyService;
import com.bin.bilibrain.service.tools.ToolService;
import com.bin.bilibrain.service.tools.VaultPublishingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UnifiedAgentService {
    public static final String ROUTE_AGENT = "agent";
    public static final String MODE_AGENT = "agent";

    private final DashScopeChatClientFactory chatClientFactory;

    private final MemorySaver memorySaver;
    private final ToolService toolService;
    private final ToolPolicyService toolPolicyService;
    private final SkillRegistryService skillRegistryService;
    private final KnowledgeBaseSearchService knowledgeBaseSearchService;
    private final VideoSummarySearchService videoSummarySearchService;
    private final VideoMapper videoMapper;
    private final VaultPublishingService vaultPublishingService;
    private final AgentScopeService agentScopeService;
    private final PendingApprovalStore pendingApprovalStore;
    private final ObjectMapper objectMapper;

    public AgentExecutionResult execute(
        String conversationId,
        Long folderId,
        String videoBvid,
        String message
    ) {
        UnifiedAgentToolBridge bridge = new UnifiedAgentToolBridge(
            toolService,
            knowledgeBaseSearchService,
            videoSummarySearchService,
            agentScopeService,
            videoMapper,
            "",
            folderId,
            videoBvid
        );
        ReactAgent agent = buildAgent(bridge, listActiveSkills(), agentScopeService.describeScope(bridge.scopeMode(), bridge.folderId(), bridge.videoBvid()));
        RunnableConfig config = RunnableConfig.builder()
            .threadId(buildAgentThreadId(conversationId, bridge))
            .build();
        try {
            NodeOutput output = agent.invokeAndGetOutput(message, config)
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.OPERATION_ERROR,
                    "统一 Agent 没有返回任何输出。",
                    HttpStatus.BAD_GATEWAY
                ));
            return adaptResult(conversationId, output, bridge);
        } catch (GraphRunnerException exception) {
            throw new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "统一 Agent 执行失败：" + exception.getMessage(),
                HttpStatus.BAD_GATEWAY
            );
        }
    }

    public AgentExecutionResult listScopeVideos(
        Long folderId,
        String videoBvid
    ) {
        UnifiedAgentToolBridge bridge = new UnifiedAgentToolBridge(
            toolService,
            knowledgeBaseSearchService,
            videoSummarySearchService,
            agentScopeService,
            videoMapper,
            "",
            folderId,
            videoBvid
        );
        bridge.listScopeVideos();
        return new AgentExecutionResult(
            buildScopeVideoListAnswer(bridge),
            ROUTE_AGENT,
            MODE_AGENT,
            "",
            List.of(),
            listActiveSkills(),
            bridge.skillEvents(),
            bridge.toolEvents(),
            null
        );
    }

    public AgentStreamRuntime createStreamRuntime(
        String conversationId,
        Long folderId,
        String videoBvid
    ) {
        UnifiedAgentToolBridge bridge = new UnifiedAgentToolBridge(
            toolService,
            knowledgeBaseSearchService,
            videoSummarySearchService,
            agentScopeService,
            videoMapper,
            "",
            folderId,
            videoBvid
        );
        List<SkillListItemVO> activeSkills = listActiveSkills();
        ReactAgent agent = buildAgent(bridge, activeSkills, agentScopeService.describeScope(bridge.scopeMode(), bridge.folderId(), bridge.videoBvid()));
        RunnableConfig config = RunnableConfig.builder()
            .threadId(buildAgentThreadId(conversationId, bridge))
            .build();
        return new AgentStreamRuntime(conversationId, activeSkills, bridge, agent, config);
    }

    public AgentExecutionResult resume(
        String conversationId,
        Long folderId,
        String videoBvid,
        AgentResumeStreamRequest request
    ) {
        InterruptionMetadata interruption = pendingApprovalStore.get(conversationId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND_ERROR,
                "当前会话没有待审批的 Agent 任务。",
                HttpStatus.NOT_FOUND
            ));

        UnifiedAgentToolBridge bridge = new UnifiedAgentToolBridge(
            toolService,
            knowledgeBaseSearchService,
            videoSummarySearchService,
            agentScopeService,
            videoMapper,
            "",
            folderId,
            videoBvid
        );
        ReactAgent agent = buildAgent(bridge, listActiveSkills(), agentScopeService.describeScope(bridge.scopeMode(), bridge.folderId(), bridge.videoBvid()));
        RunnableConfig config = RunnableConfig.builder()
            .threadId(buildAgentThreadId(conversationId, bridge))
            .addHumanFeedback(buildFeedback(interruption, request.feedbacks()))
            .build();
        try {
            NodeOutput output = agent.invokeAndGetOutput(Map.of(), config)
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.OPERATION_ERROR,
                    "恢复执行后没有拿到 Agent 输出。",
                    HttpStatus.BAD_GATEWAY
                ));
            pendingApprovalStore.remove(conversationId);
            return adaptResult(conversationId, output, bridge);
        } catch (GraphRunnerException exception) {
            throw new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "恢复 Agent 执行失败：" + exception.getMessage(),
                HttpStatus.BAD_GATEWAY
            );
        }
    }

    private ReactAgent buildAgent(
        UnifiedAgentToolBridge bridge,
        List<SkillListItemVO> activeSkills,
        String scopeDescription
    ) {
        ChatClient chatClient = chatClientFactory.createQaClient();
        if (chatClient == null) {
            throw new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "统一 Agent 依赖的聊天模型未启用。",
                HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        HumanInTheLoopHook.Builder hitlHookBuilder = HumanInTheLoopHook.builder();
        toolPolicyService.listTools().stream()
            .filter(tool -> tool.approvalRequired() && tool.enabled())
            .forEach(tool -> hitlHookBuilder.approvalOn(
                tool.name(),
                ToolConfig.builder().description(toolPolicyService.descriptionOf(tool.name())).build()
            ));

        return ReactAgent.builder()
            .name("bilibrain-unified-agent")
            .description("BiliBrain unified agent")
            .chatClient(chatClient)
            .instruction(UnifiedAgentPrompts.buildInstruction(activeSkills, scopeDescription))
            .methodTools(bridge)
            .hooks(hitlHookBuilder.build())
            .saver(memorySaver)
            .releaseThread(false)
            .build();
    }

    private AgentExecutionResult adaptResult(
        String conversationId,
        NodeOutput output,
        UnifiedAgentToolBridge bridge
    ) {
        List<SkillListItemVO> activeSkills = listActiveSkills();
        if (output instanceof InterruptionMetadata interruption) {
            InterruptionMetadata hydratedInterruption = hydratePublishApproval(interruption, bridge);
            pendingApprovalStore.put(conversationId, hydratedInterruption);
            return new AgentExecutionResult(
                "",
                ROUTE_AGENT,
                MODE_AGENT,
                "",
                deduplicateSources(bridge.collectedSources()),
                activeSkills,
                bridge.skillEvents(),
                bridge.toolEvents(),
                toApproval(conversationId, hydratedInterruption, bridge)
            );
        }

        pendingApprovalStore.remove(conversationId);
        return new AgentExecutionResult(
            resolveFinalAnswer(output, bridge),
            ROUTE_AGENT,
            MODE_AGENT,
            "",
            deduplicateSources(bridge.collectedSources()),
            activeSkills,
            bridge.skillEvents(),
            bridge.toolEvents(),
            null
        );
    }

    public AgentExecutionResult adaptStreamResult(
        String conversationId,
        NodeOutput output,
        UnifiedAgentToolBridge bridge
    ) {
        return adaptResult(conversationId, output, bridge);
    }

    private List<SkillListItemVO> listActiveSkills() {
        return skillRegistryService.listSkills().stream()
            .filter(SkillListItemVO::active)
            .sorted(Comparator.comparing(SkillListItemVO::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private String extractAssistantAnswer(NodeOutput output) {
        List<Message> messages = output.state().value("messages", List.class)
            .orElse(List.of());
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if (message instanceof AssistantMessage assistantMessage) {
                String text = assistantMessage.getText();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return "";
    }

    private String resolveFinalAnswer(NodeOutput output, UnifiedAgentToolBridge bridge) {
        if (bridge.hasScopeVideoListing()) {
            return buildScopeVideoListAnswer(bridge);
        }
        return extractAssistantAnswer(output);
    }

    private InterruptionMetadata buildFeedback(
        InterruptionMetadata interruption,
        List<AgentApprovalDecisionRequest> decisions
    ) {
        Map<String, AgentApprovalDecisionRequest> decisionMap = decisions.stream()
            .collect(java.util.stream.Collectors.toMap(
                AgentApprovalDecisionRequest::toolId,
                decision -> decision,
                (left, right) -> right
            ));

        InterruptionMetadata.Builder builder = InterruptionMetadata.builder(interruption.node(), interruption.state());
        interruption.toolFeedbacks().forEach(toolFeedback -> {
            AgentApprovalDecisionRequest decision = decisionMap.get(toolFeedback.getId());
            if (decision == null) {
                return;
            }
            builder.addToolFeedback(
                InterruptionMetadata.ToolFeedback.builder(toolFeedback)
                    .result(parseDecision(decision.decision()))
                    .arguments(resolveArguments(toolFeedback.getArguments(), decision))
                    .build()
            );
        });
        return builder.build();
    }

    private InterruptionMetadata.ToolFeedback.FeedbackResult parseDecision(String decision) {
        try {
            return InterruptionMetadata.ToolFeedback.FeedbackResult.valueOf(decision.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的审批决策。", HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveArguments(String currentArguments, AgentApprovalDecisionRequest decision) {
        if ("EDITED".equalsIgnoreCase(decision.decision())) {
            return decision.editedArguments() == null ? currentArguments : decision.editedArguments();
        }
        return currentArguments;
    }

    private AgentApprovalVO toApproval(
        String conversationId,
        InterruptionMetadata interruption,
        UnifiedAgentToolBridge bridge
    ) {
        List<AgentApprovalItemVO> items = interruption.toolFeedbacks().stream()
            .map(toolFeedback -> new AgentApprovalItemVO(
                toolFeedback.getId(),
                toolFeedback.getName(),
                toolFeedback.getArguments(),
                toolFeedback.getDescription()
            ))
            .toList();
        return new AgentApprovalVO(
            conversationId,
            bridge.scopeMode(),
            bridge.folderId(),
            bridge.videoBvid(),
            items
        );
    }

    private InterruptionMetadata hydratePublishApproval(
        InterruptionMetadata interruption,
        UnifiedAgentToolBridge bridge
    ) {
        InterruptionMetadata.Builder builder = InterruptionMetadata.builder(interruption.node(), interruption.state());
        interruption.toolFeedbacks().forEach(toolFeedback -> {
            if (!ToolService.TOOL_PUBLISH_TO_VAULT_FS.equals(toolFeedback.getName())) {
                builder.addToolFeedback(toolFeedback);
                return;
            }
            builder.addToolFeedback(
                InterruptionMetadata.ToolFeedback.builder(toolFeedback)
                    .arguments(hydratePublishArguments(toolFeedback.getArguments(), bridge))
                    .build()
            );
        });
        return builder.build();
    }

    private String hydratePublishArguments(String rawArguments, UnifiedAgentToolBridge bridge) {
        Map<String, Object> arguments = parseArguments(rawArguments);
        arguments.putIfAbsent("kind", resolvePublishKind(bridge));
        arguments.putIfAbsent("title", resolvePublishTitle(bridge));
        Object content = firstNonBlank(arguments.get("contentMarkdown"), arguments.get("content_markdown"));
        if (!(content instanceof String text) || text.trim().isEmpty()) {
            arguments.put("contentMarkdown", buildFallbackMarkdown(bridge));
            arguments.remove("content_markdown");
        }
        String kind = String.valueOf(arguments.getOrDefault("kind", ""));
        String title = String.valueOf(arguments.getOrDefault("title", ""));
        if (StringUtils.hasText(kind) && StringUtils.hasText(title)) {
            java.nio.file.Path previewPath = vaultPublishingService.previewTargetPath(kind, title);
            arguments.putIfAbsent("target_path_preview", previewPath.toString());
            arguments.putIfAbsent("file_name_preview", previewPath.getFileName().toString());
        }
        return writeJson(arguments);
    }

    private Map<String, Object> parseArguments(String rawArguments) {
        if (!StringUtils.hasText(rawArguments)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(rawArguments, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException exception) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "序列化审批参数失败。", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String resolvePublishKind(UnifiedAgentToolBridge bridge) {
        if (StringUtils.hasText(bridge.videoBvid())) {
            return "video_note";
        }
        if (bridge.folderId() != null) {
            return "folder_guide";
        }
        return "review_plan";
    }

    private String resolvePublishTitle(UnifiedAgentToolBridge bridge) {
        List<ChatSourceVO> sources = bridge.collectedSources();
        if (!sources.isEmpty()) {
            String sourceTitle = sources.get(0).videoTitle();
            if (StringUtils.hasText(sourceTitle)) {
                if ("folder_guide".equals(resolvePublishKind(bridge))) {
                    return sourceTitle.trim() + " 收藏夹总结";
                }
                return sourceTitle.trim();
            }
        }
        if (bridge.folderId() != null) {
            return "收藏夹学习总结";
        }
        if (StringUtils.hasText(bridge.videoBvid())) {
            return bridge.videoBvid().trim();
        }
        return "学习笔记";
    }

    private String buildFallbackMarkdown(UnifiedAgentToolBridge bridge) {
        String title = resolvePublishTitle(bridge);
        List<ChatSourceVO> sources = deduplicateSources(bridge.collectedSources());
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(title).append("\n\n");
        markdown.append("## 自动整理草稿\n");
        markdown.append("以下内容基于当前检索结果自动汇总，保存前可继续编辑完善。\n\n");
        if (sources.isEmpty()) {
            markdown.append("- 当前没有可直接整理的来源内容。\n");
            return markdown.toString();
        }
        for (int index = 0; index < sources.size(); index += 1) {
            ChatSourceVO source = sources.get(index);
            markdown.append("### 资料 ").append(index + 1).append("\n");
            if (StringUtils.hasText(source.videoTitle())) {
                markdown.append("- 视频：").append(source.videoTitle().trim()).append("\n");
            }
            if (StringUtils.hasText(source.upName())) {
                markdown.append("- UP 主：").append(source.upName().trim()).append("\n");
            }
            if (StringUtils.hasText(source.excerpt())) {
                markdown.append("- 摘要：").append(source.excerpt().trim()).append("\n");
            }
            markdown.append("\n");
        }
        return markdown.toString().trim();
    }

    private Object firstNonBlank(Object left, Object right) {
        if (left instanceof String l && StringUtils.hasText(l)) {
            return l;
        }
        if (right instanceof String r && StringUtils.hasText(r)) {
            return r;
        }
        return left != null ? left : right;
    }

    private List<ChatSourceVO> deduplicateSources(List<ChatSourceVO> sources) {
        List<ChatSourceVO> result = new ArrayList<>();
        for (ChatSourceVO source : sources) {
            boolean exists = result.stream().anyMatch(existing ->
                safe(existing.sourceType()).equals(safe(source.sourceType()))
                    && safe(existing.bvid()).equals(safe(source.bvid()))
                    && safe(existing.excerpt()).equals(safe(source.excerpt()))
            );
            if (!exists) {
                result.add(source);
            }
        }
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    static String buildScopeVideoListAnswer(UnifiedAgentToolBridge bridge) {
        List<UnifiedAgentToolBridge.ScopeVideoItem> videos = bridge.listedScopeVideos();
        if (videos.isEmpty()) {
            return "当前范围内没有可用视频。";
        }
        StringBuilder answer = new StringBuilder();
        answer.append("当前范围共 ").append(videos.size()).append(" 个视频：\n\n");
        for (int index = 0; index < videos.size(); index += 1) {
            UnifiedAgentToolBridge.ScopeVideoItem video = videos.get(index);
            answer.append(index + 1).append(". ")
                .append(video.title().isBlank() ? video.bvid() : video.title())
                .append(" | UP: ")
                .append(video.upName().isBlank() ? "-" : video.upName())
                .append(" | BV: ")
                .append(video.bvid());
            if (index < videos.size() - 1) {
                answer.append('\n');
            }
        }
        return answer.toString();
    }

    static String buildAgentThreadId(String conversationId, UnifiedAgentToolBridge bridge) {
        String baseConversationId = StringUtils.hasText(conversationId) ? conversationId.trim() : "anonymous";
        String scopePart = switch (bridge.scopeMode()) {
            case "video" -> "video:" + safeScopeId(bridge.videoBvid());
            case "folder" -> "folder:" + (bridge.folderId() == null ? "none" : bridge.folderId());
            default -> "global";
        };
        return baseConversationId + "::" + scopePart;
    }

    private static String safeScopeId(String value) {
        return StringUtils.hasText(value) ? value.trim() : "none";
    }

    public record AgentStreamRuntime(
        String conversationId,
        List<SkillListItemVO> activeSkills,
        UnifiedAgentToolBridge bridge,
        ReactAgent agent,
        RunnableConfig config
    ) {
    }
}
