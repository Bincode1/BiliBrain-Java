package com.bin.bilibrain.service.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.bin.bilibrain.ai.prompt.UnifiedAgentPrompts;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.model.dto.agent.AgentApprovalDecisionRequest;
import com.bin.bilibrain.model.dto.agent.AgentResumeStreamRequest;
import com.bin.bilibrain.model.vo.agent.AgentApprovalItemVO;
import com.bin.bilibrain.model.vo.agent.AgentApprovalVO;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import com.bin.bilibrain.service.retrieval.KnowledgeBaseSearchService;
import com.bin.bilibrain.service.retrieval.VideoSummarySearchService;
import com.bin.bilibrain.service.skills.SkillRegistryService;
import com.bin.bilibrain.service.tools.ToolPolicyService;
import com.bin.bilibrain.service.tools.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UnifiedAgentService {
    public static final String ROUTE_AGENT = "agent";
    public static final String MODE_AGENT = "agent";

    @Qualifier("qaChatClient")
    private final ObjectProvider<ChatClient> qaChatClientProvider;

    private final MemorySaver memorySaver;
    private final ToolService toolService;
    private final ToolPolicyService toolPolicyService;
    private final SkillRegistryService skillRegistryService;
    private final KnowledgeBaseSearchService knowledgeBaseSearchService;
    private final VideoSummarySearchService videoSummarySearchService;
    private final PendingApprovalStore pendingApprovalStore;

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
            folderId,
            videoBvid
        );
        ReactAgent agent = buildAgent(bridge, listActiveSkills(), folderId, videoBvid);
        RunnableConfig config = RunnableConfig.builder()
            .threadId(conversationId)
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
            folderId,
            videoBvid
        );
        ReactAgent agent = buildAgent(bridge, listActiveSkills(), folderId, videoBvid);
        RunnableConfig config = RunnableConfig.builder()
            .threadId(conversationId)
            .addHumanFeedback(buildFeedback(interruption, request.feedbacks()))
            .resume()
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
        Long folderId,
        String videoBvid
    ) {
        ChatClient chatClient = qaChatClientProvider.getIfAvailable();
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
            .instruction(UnifiedAgentPrompts.buildInstruction(activeSkills, folderId, videoBvid))
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
            pendingApprovalStore.put(conversationId, interruption);
            return new AgentExecutionResult(
                "",
                ROUTE_AGENT,
                MODE_AGENT,
                "命中需要人工确认的工具调用，等待审批后继续执行。",
                deduplicateSources(bridge.collectedSources()),
                activeSkills,
                bridge.skillEvents(),
                bridge.toolEvents(),
                toApproval(conversationId, interruption)
            );
        }

        pendingApprovalStore.remove(conversationId);
        return new AgentExecutionResult(
            extractAssistantAnswer(output),
            ROUTE_AGENT,
            MODE_AGENT,
            "统一 Agent 已完成本轮工具编排与回答生成。",
            deduplicateSources(bridge.collectedSources()),
            activeSkills,
            bridge.skillEvents(),
            bridge.toolEvents(),
            null
        );
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

    private AgentApprovalVO toApproval(String conversationId, InterruptionMetadata interruption) {
        List<AgentApprovalItemVO> items = interruption.toolFeedbacks().stream()
            .map(toolFeedback -> new AgentApprovalItemVO(
                toolFeedback.getId(),
                toolFeedback.getName(),
                toolFeedback.getArguments(),
                toolFeedback.getDescription()
            ))
            .toList();
        return new AgentApprovalVO(conversationId, items);
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
}
