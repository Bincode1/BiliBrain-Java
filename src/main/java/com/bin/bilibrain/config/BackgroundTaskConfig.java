package com.bin.bilibrain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class BackgroundTaskConfig {

    @Bean(name = "ingestionTaskExecutor")
    public ThreadPoolTaskExecutor ingestionTaskExecutor(AppProperties appProperties) {
        int concurrency = Math.max(appProperties.getProcessing().getIngestionMaxConcurrency(), 1);
        return buildExecutor("bilibrain-ingestion-", concurrency, concurrency, 128);
    }

    @Bean(name = "agentTaskExecutor")
    public ThreadPoolTaskExecutor agentTaskExecutor() {
        return buildExecutor("bilibrain-agent-", 2, 4, 64);
    }

    @Bean(name = "asrChunkTaskExecutor")
    public ThreadPoolTaskExecutor asrChunkTaskExecutor(AppProperties appProperties) {
        int concurrency = Math.max(appProperties.getProcessing().getAsrChunkConcurrency(), 1);
        return buildExecutor("bilibrain-asr-", concurrency, concurrency, concurrency * 4);
    }

    @Bean(name = "summaryTaskExecutor")
    public ThreadPoolTaskExecutor summaryTaskExecutor(AppProperties appProperties) {
        int concurrency = Math.max(appProperties.getSummary().getWindowConcurrency(), 1);
        return buildExecutor("bilibrain-summary-", concurrency, concurrency, concurrency * 4);
    }

    @Bean(name = "catalogTaskExecutor")
    public ThreadPoolTaskExecutor catalogTaskExecutor() {
        return buildExecutor("bilibrain-catalog-", 1, 2, 64);
    }

    private ThreadPoolTaskExecutor buildExecutor(String threadNamePrefix, int corePoolSize, int maxPoolSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
