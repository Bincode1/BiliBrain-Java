package com.bin.bilibrain.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "app.retrieval", name = "enabled", havingValue = "true")
public class MilvusConfig {

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClientV2(AppProperties appProperties) {
        AppProperties.Milvus properties = appProperties.getRetrieval().getMilvus();
        ConnectConfig.ConnectConfigBuilder<?, ?> builder = ConnectConfig.builder()
            .dbName(properties.getDatabase())
            .secure(properties.isSecure());

        if (properties.getConnectTimeoutMs() > 0) {
            builder.connectTimeoutMs(properties.getConnectTimeoutMs());
        }
        if (properties.getKeepAliveTimeMs() > 0) {
            builder.keepAliveTimeMs(properties.getKeepAliveTimeMs());
        }
        if (properties.getKeepAliveTimeoutMs() > 0) {
            builder.keepAliveTimeoutMs(properties.getKeepAliveTimeoutMs());
        }
        if (properties.getIdleTimeoutMs() > 0) {
            builder.idleTimeoutMs(properties.getIdleTimeoutMs());
        }
        if (properties.getRpcDeadlineMs() > 0) {
            builder.rpcDeadlineMs(properties.getRpcDeadlineMs());
        }

        if (StringUtils.hasText(properties.getUri())) {
            builder.uri(properties.getUri().trim());
        } else {
            String scheme = properties.isSecure() ? "https://" : "http://";
            builder.uri(scheme + properties.getHost().trim() + ":" + properties.getPort());
        }
        if (StringUtils.hasText(properties.getToken())) {
            builder.token(properties.getToken().trim());
        } else {
            if (StringUtils.hasText(properties.getUsername())) {
                builder.username(properties.getUsername().trim());
            }
            if (StringUtils.hasText(properties.getPassword())) {
                builder.password(properties.getPassword().trim());
            }
        }
        return new MilvusClientV2(builder.build());
    }
}
