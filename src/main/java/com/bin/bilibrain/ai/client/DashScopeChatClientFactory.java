package com.bin.bilibrain.ai.client;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.bin.bilibrain.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DashScopeChatClientFactory {
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AppProperties appProperties;
    private final Environment environment;

    public boolean isAvailable() {
        return chatModelProvider.getIfAvailable() != null;
    }

    public ChatClient createQaClient() {
        return createClient(0.3);
    }

    public ChatClient createSummaryClient() {
        return createClient(appProperties.getSummary().getTemperature());
    }

    private ChatClient createClient(double temperature) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return null;
        }
        DashScopeChatOptions options = DashScopeChatOptions.builder()
            .model(environment.getProperty("spring.ai.dashscope.chat.options.model", "qwen3.5-plus"))
            .multiModel(true)
            .temperature(temperature)
            .build();
        return ChatClient.builder(chatModel)
            .defaultOptions(options)
            .build();
    }
}
