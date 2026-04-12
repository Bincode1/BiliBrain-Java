package com.bin.bilibrain.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean("qaChatClient")
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnProperty(prefix = "spring.ai.dashscope.chat", name = "enabled", havingValue = "true")
    public ChatClient qaChatClient(
        ChatModel chatModel,
        @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}") String model
    ) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
            .model(model)
            .temperature(0.3)
            .build();
        return ChatClient.builder(chatModel)
            .defaultOptions(options)
            .build();
    }
}
