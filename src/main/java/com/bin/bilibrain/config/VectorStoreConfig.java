package com.bin.bilibrain.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.exception.ParamException;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientProperties;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties({MilvusServiceClientProperties.class, MilvusVectorStoreProperties.class})
@ConditionalOnProperty(prefix = "app.retrieval", name = "enabled", havingValue = "true")
public class VectorStoreConfig {

    @Bean
    public MilvusServiceClient milvusServiceClient(
        MilvusServiceClientProperties clientProperties,
        MilvusVectorStoreProperties vectorStoreProperties
    ) throws ParamException {
        ConnectParam.Builder builder = ConnectParam.newBuilder();
        if (StringUtils.hasText(clientProperties.getUri())) {
            builder.withUri(clientProperties.getUri().trim());
        } else {
            builder.withHost(clientProperties.getHost());
            builder.withPort(clientProperties.getPort());
        }
        if (StringUtils.hasText(vectorStoreProperties.getDatabaseName())) {
            builder.withDatabaseName(vectorStoreProperties.getDatabaseName().trim());
        }
        if (StringUtils.hasText(clientProperties.getToken())) {
            builder.withToken(clientProperties.getToken().trim());
        } else if (StringUtils.hasText(clientProperties.getUsername()) && StringUtils.hasText(clientProperties.getPassword())) {
            builder.withAuthorization(clientProperties.getUsername().trim(), clientProperties.getPassword().trim());
        }
        if (clientProperties.getConnectTimeoutMs() > 0) {
            builder.withConnectTimeout(clientProperties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
        }
        if (clientProperties.getKeepAliveTimeMs() > 0) {
            builder.withKeepAliveTime(clientProperties.getKeepAliveTimeMs(), TimeUnit.MILLISECONDS);
        }
        if (clientProperties.getKeepAliveTimeoutMs() > 0) {
            builder.withKeepAliveTimeout(clientProperties.getKeepAliveTimeoutMs(), TimeUnit.MILLISECONDS);
        }
        if (clientProperties.getIdleTimeoutMs() > 0) {
            builder.withIdleTimeout(clientProperties.getIdleTimeoutMs(), TimeUnit.MILLISECONDS);
        }
        if (clientProperties.getRpcDeadlineMs() > 0) {
            builder.withRpcDeadline(clientProperties.getRpcDeadlineMs(), TimeUnit.MILLISECONDS);
        }
        if (clientProperties.isSecure()) {
            builder.withSecure(true);
        }
        copyTlsPaths(clientProperties, builder);
        return new MilvusServiceClient(builder.build());
    }

    @Bean
    @ConditionalOnBean({MilvusServiceClient.class, EmbeddingModel.class})
    public VectorStore milvusVectorStore(
        MilvusServiceClient milvusServiceClient,
        EmbeddingModel embeddingModel,
        MilvusVectorStoreProperties properties
    ) {
        MilvusVectorStore.Builder builder = MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
            .databaseName(properties.getDatabaseName())
            .collectionName(properties.getCollectionName())
            .embeddingDimension(properties.getEmbeddingDimension())
            .indexType(toIndexType(properties))
            .metricType(toMetricType(properties))
            .indexParameters(properties.getIndexParameters())
            .initializeSchema(properties.isInitializeSchema())
            .autoId(properties.isAutoId());

        if (StringUtils.hasText(properties.getIdFieldName())) {
            builder.iDFieldName(properties.getIdFieldName().trim());
        }
        if (StringUtils.hasText(properties.getContentFieldName())) {
            builder.contentFieldName(properties.getContentFieldName().trim());
        }
        if (StringUtils.hasText(properties.getMetadataFieldName())) {
            builder.metadataFieldName(properties.getMetadataFieldName().trim());
        }
        if (StringUtils.hasText(properties.getEmbeddingFieldName())) {
            builder.embeddingFieldName(properties.getEmbeddingFieldName().trim());
        }
        return builder.build();
    }

    private void copyTlsPaths(MilvusServiceClientProperties clientProperties, ConnectParam.Builder builder) {
        if (StringUtils.hasText(clientProperties.getClientKeyPath())) {
            builder.withClientKeyPath(clientProperties.getClientKeyPath().trim());
        }
        if (StringUtils.hasText(clientProperties.getClientPemPath())) {
            builder.withClientPemPath(clientProperties.getClientPemPath().trim());
        }
        if (StringUtils.hasText(clientProperties.getCaPemPath())) {
            builder.withCaPemPath(clientProperties.getCaPemPath().trim());
        }
        if (StringUtils.hasText(clientProperties.getServerPemPath())) {
            builder.withServerPemPath(clientProperties.getServerPemPath().trim());
        }
        if (StringUtils.hasText(clientProperties.getServerName())) {
            builder.withServerName(clientProperties.getServerName().trim());
        }
    }

    private IndexType toIndexType(MilvusVectorStoreProperties properties) {
        return IndexType.valueOf(properties.getIndexType().name());
    }

    private MetricType toMetricType(MilvusVectorStoreProperties properties) {
        return MetricType.valueOf(properties.getMetricType().name());
    }
}
