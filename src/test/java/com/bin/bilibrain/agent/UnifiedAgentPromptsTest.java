package com.bin.bilibrain.agent;

import com.bin.bilibrain.ai.prompt.UnifiedAgentPrompts;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedAgentPromptsTest {

    @Test
    void promptHighlightsBilibiliScopeSummaryRetrievalAndGradualSkillLoading() {
        String prompt = UnifiedAgentPrompts.buildInstruction(
            List.of(new SkillListItemVO("java-rag", "负责 Java RAG 问答", "skills/java-rag/SKILL.md", true)),
            "当前范围是单个收藏夹：Java AI（folder_id=3003）。"
        );

        assertThat(prompt).contains("B 站视频与收藏夹知识库");
        assertThat(prompt).contains("当前范围是单个收藏夹：Java AI（folder_id=3003）。");
        assertThat(prompt).contains("先调用 `list_scope_videos`");
        assertThat(prompt).contains("只有在 `list_scope_videos` 真正返回了结果后");
        assertThat(prompt).contains("禁止根据记忆、摘要检索、知识库检索或猜测来声明视频数量与清单");
        assertThat(prompt).contains("不要自行按主题重新分组");
        assertThat(prompt).contains("总结、概括、归纳、梳理收藏夹 / 当前范围内容时，优先调用 `search_video_summaries`");
        assertThat(prompt).contains("只有任务明显匹配某个 skill 时，才调用 `read_skill`");
        assertThat(prompt).contains("java-rag: 负责 Java RAG 问答");
        assertThat(prompt).doesNotContain("list_workspaces");
    }
}
