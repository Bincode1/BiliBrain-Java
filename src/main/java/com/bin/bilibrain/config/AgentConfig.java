package com.bin.bilibrain.config;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class AgentConfig {

    @Bean
    public SkillRegistry skillRegistry(AppProperties appProperties, ProjectPathResolver projectPathResolver) {
        return FileSystemSkillRegistry.builder()
            .projectSkillsDirectory(
                projectPathResolver.resolveFromProjectRoot(appProperties.getStorage().getSkillsRoot()).toString()
            )
            .build();
    }

    @Bean
    public MemorySaver memorySaver(DataSource dataSource) {
        return MysqlSaver.builder()
            .dataSource(dataSource)
            .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
            .build();
    }
}
