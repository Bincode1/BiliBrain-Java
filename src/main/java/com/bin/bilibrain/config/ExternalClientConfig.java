package com.bin.bilibrain.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class ExternalClientConfig {

    @Bean
    @Qualifier("qwenAsrWebClient")
    public WebClient qwenAsrWebClient(WebClient.Builder builder, AppProperties appProperties) {
        int maxConnections = Math.max(appProperties.getProcessing().getAsrChunkConcurrency() * 2, 8);
        return builder.clone()
            .baseUrl(appProperties.getProcessing().getAsrApiBaseUrl())
            .clientConnector(new ReactorClientHttpConnector(buildHttpClient(
                "bilibrain-qwen-asr",
                maxConnections,
                appProperties.getProcessing().getAsrApiTimeoutSeconds()
            )))
            .build();
    }

    @Bean
    @Qualifier("bilibiliWebClient")
    public WebClient bilibiliWebClient(WebClient.Builder builder, AppProperties appProperties) {
        int maxConnections = Math.max(appProperties.getProcessing().getIngestionMaxConcurrency() * 4, 16);
        return builder.clone()
            .clientConnector(new ReactorClientHttpConnector(buildHttpClient(
                "bilibrain-bilibili",
                maxConnections,
                appProperties.getBilibili().getHttpTimeoutSeconds()
            )))
            .build();
    }

    private HttpClient buildHttpClient(String providerName, int maxConnections, int responseTimeoutSeconds) {
        ConnectionProvider provider = ConnectionProvider.builder(providerName)
            .maxConnections(maxConnections)
            .pendingAcquireTimeout(Duration.ofSeconds(5))
            .build();
        return HttpClient.create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(Math.max(responseTimeoutSeconds, 1)));
    }
}
