package com.bin.bilibrain.service.agent;

import com.bin.bilibrain.ai.client.DashScopeChatClientFactory;
import com.bin.bilibrain.model.vo.chat.ChatCitationSegmentVO;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CitationAttributionService {
    private final DashScopeChatClientFactory chatClientFactory;
    private final ObjectMapper objectMapper;

    public List<ChatCitationSegmentVO> attribute(String answer, List<ChatSourceVO> sources) {
        if (!StringUtils.hasText(answer) || sources == null || sources.isEmpty()) {
            return List.of();
        }
        ChatClient chatClient = chatClientFactory.createQaClient();
        if (chatClient == null) {
            return List.of();
        }
        List<String> units = splitAnswerUnits(answer);
        if (units.isEmpty()) {
            return List.of();
        }
        try {
            String content = chatClient.prompt()
                .system(systemPrompt())
                .user(userPrompt(units, sources))
                .call()
                .content();
            if (!StringUtils.hasText(content)) {
                return List.of();
            }
            CitationPayload payload = objectMapper.readValue(stripMarkdownFence(content), CitationPayload.class);
            return rebuildSegments(units, payload == null ? List.of() : payload.items(), sources.size());
        } catch (Exception exception) {
            log.warn("citation attribution failed: {}", exception.getMessage());
            return List.of();
        }
    }

    private List<ChatCitationSegmentVO> rebuildSegments(
        List<String> units,
        List<CitationIndexItem> items,
        int sourceCount
    ) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<Integer>> refMap = new HashMap<>();
        for (CitationIndexItem item : items) {
            if (item == null || item.index() == null) {
                continue;
            }
            int unitIndex = item.index();
            if (unitIndex < 1 || unitIndex > units.size()) {
                continue;
            }
            refMap.put(unitIndex, sanitizeRefs(item.sourceRefs(), sourceCount));
        }
        List<ChatCitationSegmentVO> result = new ArrayList<>();
        for (int index = 0; index < units.size(); index += 1) {
            String unit = units.get(index);
            List<Integer> refs = refMap.getOrDefault(index + 1, List.of());
            if (!result.isEmpty() && result.getLast().sourceRefs().equals(refs)) {
                ChatCitationSegmentVO previous = result.removeLast();
                result.add(new ChatCitationSegmentVO(previous.text() + unit, refs));
                continue;
            }
            result.add(new ChatCitationSegmentVO(unit, refs));
        }
        return result;
    }

    private List<Integer> sanitizeRefs(List<Integer> refs, int sourceCount) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer ref : refs) {
            if (ref != null && ref > 0 && ref <= sourceCount) {
                normalized.add(ref);
            }
        }
        return List.copyOf(normalized);
    }

    private String stripMarkdownFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutStart = trimmed.replaceFirst("^```(?:json)?\\s*", "");
        return withoutStart.replaceFirst("\\s*```$", "").trim();
    }

    private String systemPrompt() {
        return """
            你是一个回答来源归因器。
            你的任务不是改写答案，而是为给定的“答案单元编号”标注它依赖的来源编号。

            强约束：
            1. 只能输出 JSON，对象格式固定为 {"items":[{"index":1,"source_refs":[1,2]}]}。
            2. index 只能填写已提供的答案单元编号。
            3. 不要改写答案，不要输出答案原文，不要补充解释。
            4. 某个单元如果没有明确来源支撑，就让它的 source_refs 为 []。
            5. source_refs 只能填写提供的来源编号，且按升序去重。
            6. 只根据提供来源判断，不要猜测来源。
            """;
    }

    private String userPrompt(List<String> units, List<ChatSourceVO> sources) {
        StringBuilder unitBlock = new StringBuilder();
        for (int index = 0; index < units.size(); index += 1) {
            unitBlock.append("[").append(index + 1).append("] ")
                .append(units.get(index).replace("\n", "\\n"))
                .append("\n");
        }
        StringBuilder sourceBlock = new StringBuilder();
        for (int index = 0; index < sources.size(); index += 1) {
            ChatSourceVO source = sources.get(index);
            sourceBlock.append("[").append(index + 1).append("]\n")
                .append("标题：").append(safe(source.videoTitle())).append("\n")
                .append("UP：").append(safe(source.upName())).append("\n")
                .append("BV：").append(safe(source.bvid())).append("\n")
                .append("类型：").append(safe(source.sourceType())).append("\n")
                .append("时间：").append(safe(source.startSeconds())).append(" - ").append(safe(source.endSeconds())).append("\n")
                .append("内容：").append(safe(source.excerpt())).append("\n\n");
        }
        return """
            答案单元：
            %s

            可引用来源：
            %s

            现在请输出 JSON。
            """.formatted(unitBlock.toString().trim(), sourceBlock.toString().trim());
    }

    private List<String> splitAnswerUnits(String answer) {
        List<String> units = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String text = answer == null ? "" : answer;
        for (int index = 0; index < text.length(); index += 1) {
            char ch = text.charAt(index);
            current.append(ch);
            boolean shouldFlush = ch == '\n'
                || ch == '。'
                || ch == '！'
                || ch == '？'
                || ch == '；';
            if (!shouldFlush) {
                continue;
            }
            units.add(current.toString());
            current.setLength(0);
        }
        if (!current.isEmpty()) {
            units.add(current.toString());
        }
        return units;
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CitationPayload(List<CitationIndexItem> items) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CitationIndexItem(Integer index, List<Integer> sourceRefs) {
    }
}
