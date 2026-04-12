package com.bin.bilibrain.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class VectorStoreConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.retrieval", name = "enabled", havingValue = "true")
    public ChromaApi chromaApi(
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper,
        @Value("${spring.ai.vectorstore.chroma.client.host}") String host,
        @Value("${spring.ai.vectorstore.chroma.client.port}") int port
    ) {
        String baseUrl = "http://" + host + ":" + port;
        if (host.startsWith("http://") || host.startsWith("https://")) {
            baseUrl = host;
            if (!host.contains(":" + port)) {
                baseUrl = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
                baseUrl = baseUrl + ":" + port;
            }
        }
        return new ChromaApi(baseUrl, restClientBuilder, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.retrieval", name = "enabled", havingValue = "true")
    public VectorStore chromaVectorStore(
        ChromaApi chromaApi,
        EmbeddingModel embeddingModel,
        @Value("${spring.ai.vectorstore.chroma.tenant-name}") String tenantName,
        @Value("${spring.ai.vectorstore.chroma.database-name}") String databaseName,
        @Value("${spring.ai.vectorstore.chroma.collection-name}") String collectionName,
        @Value("${spring.ai.vectorstore.chroma.initialize-schema:true}") boolean initializeSchema
    ) {
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
            .tenantName(tenantName)
            .databaseName(databaseName)
            .collectionName(collectionName)
            .initializeSchema(initializeSchema)
            .build();
    }
}
