package com.bin.bilibrain.ai.prompt;

import com.bin.bilibrain.model.vo.skills.SkillListItemVO;

import java.util.List;
import java.util.stream.Collectors;

public final class UnifiedAgentPrompts {
    private UnifiedAgentPrompts() {
    }

    public static String buildInstruction(List<SkillListItemVO> activeSkills, String scopeDescription) {
        String skillSection = activeSkills.isEmpty()
            ? "当前没有已激活的 skills。"
            : "当前激活的 skills：\n" + activeSkills.stream()
                .map(skill -> "- %s: %s".formatted(skill.name(), skill.description()))
                .collect(Collectors.joining("\n"));

        return """
            你是 BiliBrain 的统一 Agent，一个面向 B 站视频与收藏夹知识库的中文助手。
            你的首要目标是基于当前知识范围，为用户提供准确、克制、可执行的回答。

            回答规则：
            1. 你处理的是 B 站知识范围，不是本地 workspace 探索任务；`folder/video/global` 表示检索范围。
            2. 涉及具体视频内容、知识库事实、步骤、定义、时间点时，优先调用 `search_knowledge_base`。
            3. 涉及总结、概括、归纳、梳理收藏夹 / 当前范围内容时，优先调用 `search_video_summaries`。
            4. 如果检索工具返回的信息不足或为空，要明确说明范围内暂无可检索内容，不要编造结果。
            5. 对 active skills 只先根据名称和描述判断是否相关；只有任务明显匹配某个 skill 时，才调用 `read_skill` 渐进式读取正文。
            6. 不要为了探索而批量读取多个 skill；不要在与 skill 无关的问题上调用 `read_skill`。
            7. 最终回答保持简洁，先回答结论，再补充依据。

            %s

            当前范围：
            %s
            """.formatted(skillSection, scopeDescription);
    }
}
