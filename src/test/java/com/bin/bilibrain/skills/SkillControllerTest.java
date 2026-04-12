package com.bin.bilibrain.skills;

import com.bin.bilibrain.controller.SkillController;
import com.bin.bilibrain.exception.GlobalExceptionHandler;
import com.bin.bilibrain.model.vo.skills.SkillDetailVO;
import com.bin.bilibrain.model.vo.skills.SkillListItemVO;
import com.bin.bilibrain.service.skills.SkillRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SkillController.class)
@Import(GlobalExceptionHandler.class)
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillRegistryService skillRegistryService;

    @Test
    void listSkillsReturnsItems() throws Exception {
        when(skillRegistryService.listSkills()).thenReturn(List.of(
            new SkillListItemVO("java-rag", "负责 Java RAG 问答", "skills/java-rag/SKILL.md", true)
        ));

        mockMvc.perform(get("/api/skills"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].name").value("java-rag"))
            .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void getSkillReturnsDetail() throws Exception {
        when(skillRegistryService.getSkillDetail("java-rag")).thenReturn(
            new SkillDetailVO("java-rag", "负责 Java RAG 问答", "skills/java-rag/SKILL.md", "技能正文", true)
        );

        mockMvc.perform(get("/api/skills/java-rag"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("java-rag"))
            .andExpect(jsonPath("$.data.content").value("技能正文"));
    }

    @Test
    void createSkillReturnsCreatedSkill() throws Exception {
        when(skillRegistryService.createSkill(any())).thenReturn(
            new SkillDetailVO("workspace-agent", "管理工作区", "skills/workspace-agent/SKILL.md", "技能正文", false)
        );

        mockMvc.perform(post("/api/skills/create")
                .contentType("application/json")
                .content("""
                    {"name":"workspace-agent","description":"管理工作区","content":"技能正文"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("workspace-agent"));
    }

    @Test
    void activateAndDeactivateSkillReturnSuccess() throws Exception {
        doNothing().when(skillRegistryService).activateSkill("java-rag");
        doNothing().when(skillRegistryService).deactivateSkill("java-rag");

        mockMvc.perform(post("/api/skills/activate")
                .contentType("application/json")
                .content("""
                    {"name":"java-rag"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/api/skills/deactivate")
                .contentType("application/json")
                .content("""
                    {"name":"java-rag"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));
    }
}
