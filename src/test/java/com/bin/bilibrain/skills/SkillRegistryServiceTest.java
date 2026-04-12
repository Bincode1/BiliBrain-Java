package com.bin.bilibrain.skills;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.model.dto.skills.SkillCreateRequest;
import com.bin.bilibrain.model.vo.skills.SkillDetailVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import com.bin.bilibrain.service.skills.SkillActivationService;
import com.bin.bilibrain.service.skills.SkillRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillRegistryServiceTest {

    @TempDir
    Path tempDir;

    private SkillActivationService skillActivationService;
    private SkillRegistryService skillRegistryService;

    @BeforeEach
    void setUp() {
        skillActivationService = mock(SkillActivationService.class);
        SkillRegistry skillRegistry = FileSystemSkillRegistry.builder()
            .projectSkillsDirectory(tempDir.toString())
            .build();
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setSkillsRoot(tempDir);
        skillRegistryService = new SkillRegistryService(skillRegistry, skillActivationService, appProperties);
        when(skillActivationService.isActive("java-rag")).thenReturn(true);
        when(skillActivationService.isActive("workspace-agent")).thenReturn(false);
    }

    @Test
    void createSkillWritesSkillMarkdownAndReturnsDetail() throws Exception {
        SkillDetailVO result = skillRegistryService.createSkill(new SkillCreateRequest(
            "java-rag",
            "负责 Java RAG 问答",
            "你要优先基于知识库回答问题。"
        ));

        Path skillFile = tempDir.resolve("java-rag").resolve("SKILL.md");

        assertThat(Files.exists(skillFile)).isTrue();
        assertThat(Files.readString(skillFile)).contains("name: java-rag");
        assertThat(Files.readString(skillFile)).contains("description: 负责 Java RAG 问答");
        assertThat(result.name()).isEqualTo("java-rag");
        assertThat(result.active()).isTrue();
        assertThat(result.content()).contains("优先基于知识库回答问题");
    }

    @Test
    void listSkillsReturnsSortedItemsWithActivationState() {
        skillRegistryService.createSkill(new SkillCreateRequest("workspace-agent", "管理工作区", "处理工作区任务"));
        skillRegistryService.createSkill(new SkillCreateRequest("java-rag", "负责 Java RAG 问答", "回答知识库问题"));

        List<SkillListItemVO> result = skillRegistryService.listSkills();

        assertThat(result).extracting(SkillListItemVO::name).containsExactly("java-rag", "workspace-agent");
        assertThat(result.get(0).active()).isTrue();
        assertThat(result.get(1).active()).isFalse();
    }
}
