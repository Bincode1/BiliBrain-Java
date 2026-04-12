package com.bin.bilibrain.config;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public SkillRegistry skillRegistry(AppProperties appProperties) {
        return FileSystemSkillRegistry.builder()
            .projectSkillsDirectory(appProperties.getStorage().getSkillsRoot().toAbsolutePath().normalize().toString())
            .build();
    }

    @Bean
    public MemorySaver memorySaver() {
        return MemorySaver.builder().build();
    }
}
