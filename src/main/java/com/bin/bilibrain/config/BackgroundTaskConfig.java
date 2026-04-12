package com.bin.bilibrain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class BackgroundTaskConfig {

    @Bean(name = "applicationTaskExecutor")
    @Primary
    public Executor applicationTaskExecutor(AppProperties appProperties) {
        int concurrency = Math.max(appProperties.getProcessing().getIngestionMaxConcurrency(), 2);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("bilibrain-bg-");
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency + 2);
        executor.setQueueCapacity(128);
        executor.initialize();
        return executor;
    }

    @Bean(name = "agentTaskExecutor")
    public Executor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("bilibrain-agent-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.initialize();
        return executor;
    }
}
