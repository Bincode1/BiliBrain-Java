package com.bin.bilibrain.system;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.model.vo.system.SystemReadinessVO;
import com.bin.bilibrain.service.system.SystemReadinessService;
import io.milvus.client.MilvusServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemReadinessServiceTest {
    private static final List<String> REQUIRED_TABLES = List.of(
        "processing_settings",
        "app_state",
        "folders",
        "videos",
        "transcripts",
        "video_summaries",
        "video_pipeline",
        "ingestion_tasks",
        "chat_conversations",
        "chat_messages",
        "chat_conversation_memory",
        "chat_conversation_context_stats",
        "tool_workspaces",
        "tool_calls",
        "skill_activations",
        "graph_checkpoint",
        "graph_thread"
    );

    @TempDir
    Path tempDir;

    @Test
    void getReadinessReturnsReadyWhenAllChecksPass() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
            .thenReturn(REQUIRED_TABLES);

        AppProperties appProperties = createAppProperties(true);
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.ai.dashscope.api-key", "test-key")
            .withProperty("spring.ai.dashscope.chat.enabled", "true")
            .withProperty("spring.ai.model.embedding", "dashscope")
            .withProperty("spring.ai.model.audio.transcription", "dashscope")
            .withProperty("spring.ai.vectorstore.milvus.database-name", "default");

        MilvusServiceClient milvusServiceClient = mock(MilvusServiceClient.class);
        when(milvusServiceClient.clientIsReady()).thenReturn(true);

        SystemReadinessService service = new SystemReadinessService(
            jdbcTemplate,
            appProperties,
            environment,
            providerOf(MilvusServiceClient.class, milvusServiceClient),
            providerOf(ChatModel.class, mock(ChatModel.class)),
            providerOf(EmbeddingModel.class, mock(EmbeddingModel.class)),
            providerOf(TranscriptionModel.class, mock(TranscriptionModel.class))
        );

        SystemReadinessVO readiness = service.getReadiness();

        assertThat(readiness.status()).isEqualTo("ready");
        assertThat(readiness.checks()).allMatch(check -> "ready".equals(check.status()));
    }

    @Test
    void getReadinessMarksDisabledCapabilitiesWhenOptionalModelsAreOff() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
            .thenReturn(REQUIRED_TABLES);

        AppProperties appProperties = createAppProperties(false);
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.ai.dashscope.chat.enabled", "false")
            .withProperty("spring.ai.model.embedding", "none")
            .withProperty("spring.ai.model.audio.transcription", "none");

        SystemReadinessService service = new SystemReadinessService(
            jdbcTemplate,
            appProperties,
            environment,
            emptyProvider(MilvusServiceClient.class),
            emptyProvider(ChatModel.class),
            emptyProvider(EmbeddingModel.class),
            emptyProvider(TranscriptionModel.class)
        );

        SystemReadinessVO readiness = service.getReadiness();

        assertThat(readiness.status()).isEqualTo("degraded");
        assertThat(readiness.checks()).anyMatch(check -> "milvus".equals(check.key()) && "disabled".equals(check.status()));
        assertThat(readiness.checks()).anyMatch(check -> "dashscope_chat".equals(check.key()) && "disabled".equals(check.status()));
    }

    private AppProperties createAppProperties(boolean retrievalEnabled) {
        AppProperties appProperties = new AppProperties();
        appProperties.getRetrieval().setEnabled(retrievalEnabled);
        appProperties.getStorage().setSkillsRoot(tempDir.resolve("skills"));
        appProperties.getStorage().setToolsWorkspaceRoot(tempDir.resolve("workspaces"));
        return appProperties;
    }

    private <T> ObjectProvider<T> providerOf(Class<T> type, T bean) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton(type.getName(), bean);
        return beanFactory.getBeanProvider(type);
    }

    private <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        return beanFactory.getBeanProvider(type);
    }
}
