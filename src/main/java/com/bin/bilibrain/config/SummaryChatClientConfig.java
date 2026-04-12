package com.bin.bilibrain.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class SummaryChatClientConfig {

    @Bean("summaryChatClient")
    @ConditionalOnBean(ChatModel.class)
    public ChatClient summaryChatClient(
        ChatModel chatModel,
        AppProperties appProperties,
        @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}") String model
    ) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
            .model(model)
            .temperature(appProperties.getSummary().getTemperature())
            .build();
        return ChatClient.builder(chatModel)
            .defaultOptions(options)
            .build();
    }
}
