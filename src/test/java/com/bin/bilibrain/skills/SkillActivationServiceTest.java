package com.bin.bilibrain.skills;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.bin.bilibrain.mapper.SkillActivationMapper;
import com.bin.bilibrain.model.entity.SkillActivation;
import com.bin.bilibrain.service.skills.SkillActivationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillActivationServiceTest {

    @Test
    void activateSkillInsertsNewActivationWhenAbsent() {
        SkillActivationMapper mapper = mock(SkillActivationMapper.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        when(skillRegistry.contains("java-rag")).thenReturn(true);
        when(mapper.selectById("java-rag")).thenReturn(null);

        SkillActivationService service = new SkillActivationService(mapper, skillRegistry);
        service.activateSkill("java-rag");

        ArgumentCaptor<SkillActivation> captor = ArgumentCaptor.forClass(SkillActivation.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getSkillName()).isEqualTo("java-rag");
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    void deactivateSkillUpdatesExistingActivation() {
        SkillActivationMapper mapper = mock(SkillActivationMapper.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        when(skillRegistry.contains("java-rag")).thenReturn(true);
        when(mapper.selectById("java-rag")).thenReturn(SkillActivation.builder()
            .skillName("java-rag")
            .isActive(true)
            .build());

        SkillActivationService service = new SkillActivationService(mapper, skillRegistry);
        service.deactivateSkill("java-rag");

        ArgumentCaptor<SkillActivation> captor = ArgumentCaptor.forClass(SkillActivation.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsActive()).isFalse();
    }
}
