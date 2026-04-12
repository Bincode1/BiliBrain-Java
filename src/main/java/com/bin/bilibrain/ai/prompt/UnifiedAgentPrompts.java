package com.bin.bilibrain.ai.prompt;

import com.bin.bilibrain.model.vo.skills.SkillListItemVO;

import java.util.List;
import java.util.stream.Collectors;

public final class UnifiedAgentPrompts {
    private UnifiedAgentPrompts() {
    }

    public static String buildInstruction(List<SkillListItemVO> activeSkills, Long folderId, String videoBvid) {
        String skillSection = activeSkills.isEmpty()
            ? "当前没有激活的 skills。"
            : "当前激活的 skills：\n" + activeSkills.stream()
                .map(skill -> "- %s: %s".formatted(skill.name(), skill.description()))
                .collect(Collectors.joining("\n"));

        String scopeSection = """
            当前知识范围：
            - folderId: %s
            - videoBvid: %s
            """.formatted(folderId == null ? "" : folderId, videoBvid == null ? "" : videoBvid);

        return """
            你是 BiliBrain 的统一 Agent。
            你的目标是结合知识检索、skills 和 workspace 工具，用中文给出准确、克制、可执行的回答。
            回答规则：
            1. 涉及具体视频内容、知识库事实时，优先调用检索工具，不要凭空编造。
            2. 涉及 skills 能力时，先调用 `read_skill` 读取技能正文，再决定是否采用。
            3. 涉及工作区时，先调用 `list_workspaces` 了解当前工作区，再继续回答。
            4. 如果工具返回的信息不足，要明确说明限制，不要伪造结果。
            5. 最终回答保持简洁，先回答结论，再补充依据。

            %s

            %s
            """.formatted(skillSection, scopeSection);
    }
}
