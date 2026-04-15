package com.bin.bilibrain.service.system;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.model.vo.system.ReadinessCheckVO;
import com.bin.bilibrain.model.vo.system.SystemReadinessVO;
import io.milvus.v2.client.MilvusClientV2;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SystemReadinessService {
    private static final Set<String> REQUIRED_TABLES = Set.of(
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

    private final JdbcTemplate jdbcTemplate;
    private final AppProperties appProperties;
    private final Environment environment;
    private final ObjectProvider<MilvusClientV2> milvusClientProvider;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final ObjectProvider<TranscriptionModel> transcriptionModelProvider;

    public SystemReadinessVO getReadiness() {
        List<ReadinessCheckVO> checks = List.of(
            checkMysqlSchema(),
            checkMilvus(),
            checkDashScopeChat(),
            checkDashScopeEmbedding(),
            checkDashScopeTranscription(),
            checkSkillsRoot(),
            checkToolsWorkspace()
        );
        boolean allReady = checks.stream().allMatch(check -> "ready".equals(check.status()));
        return new SystemReadinessVO(allReady ? "ready" : "degraded", checks);
    }

    private ReadinessCheckVO checkMysqlSchema() {
        try {
            Set<String> existingTables = new LinkedHashSet<>();
            jdbcTemplate.queryForList(
                "SELECT LOWER(TABLE_NAME) FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class
            ).forEach(existingTables::add);
            List<String> missing = REQUIRED_TABLES.stream()
                .filter(table -> !existingTables.contains(table))
                .sorted()
                .toList();
            if (missing.isEmpty()) {
                return new ReadinessCheckVO("mysql_schema", "ready", "MySQL 核心表与 Agent checkpoint 表已就绪。");
            }
            return new ReadinessCheckVO("mysql_schema", "failed", "缺少表: " + String.join(", ", missing));
        } catch (Exception exception) {
            return new ReadinessCheckVO("mysql_schema", "failed", "MySQL schema 检查失败: " + exception.getMessage());
        }
    }

    private ReadinessCheckVO checkMilvus() {
        if (!appProperties.getRetrieval().isEnabled()) {
            return new ReadinessCheckVO("milvus", "disabled", "向量检索未启用。");
        }
        MilvusClientV2 milvusClient = milvusClientProvider.getIfAvailable();
        if (milvusClient == null) {
            return new ReadinessCheckVO("milvus", "failed", "Milvus 客户端 Bean 不存在，请检查向量库配置。");
        }
        try {
            boolean ready = milvusClient.clientIsReady();
            if (!ready) {
                return new ReadinessCheckVO("milvus", "failed", "Milvus 客户端已创建，但当前未就绪。");
            }
            return new ReadinessCheckVO(
                "milvus",
                "ready",
                "Milvus 已可连通，目标库为 " + appProperties.getRetrieval().getMilvus().getDatabase() + "。"
            );
        } catch (Exception exception) {
            return new ReadinessCheckVO("milvus", "failed", "Milvus 连接失败: " + exception.getMessage());
        }
    }

    private ReadinessCheckVO checkDashScopeChat() {
        return checkModel(
            "dashscope_chat",
            environment.getProperty("spring.ai.dashscope.chat.enabled", "false"),
            chatModelProvider.getIfAvailable() != null,
            "聊天模型"
        );
    }

    private ReadinessCheckVO checkDashScopeEmbedding() {
        String configuredModel = environment.getProperty("spring.ai.model.embedding", "none");
        boolean enabled = !"none".equalsIgnoreCase(configuredModel);
        return checkModel(
            "dashscope_embedding",
            String.valueOf(enabled),
            embeddingModelProvider.getIfAvailable() != null,
            "Embedding 模型"
        );
    }

    private ReadinessCheckVO checkDashScopeTranscription() {
        String configuredModel = environment.getProperty("spring.ai.model.audio.transcription", "none");
        boolean enabled = !"none".equalsIgnoreCase(configuredModel);
        return checkModel(
            "dashscope_transcription",
            String.valueOf(enabled),
            transcriptionModelProvider.getIfAvailable() != null,
            "转写模型"
        );
    }

    private ReadinessCheckVO checkModel(String key, String enabledProperty, boolean beanAvailable, String label) {
        boolean enabled = Boolean.parseBoolean(enabledProperty);
        if (!enabled) {
            return new ReadinessCheckVO(key, "disabled", label + "未启用。");
        }
        if (!StringUtils.hasText(environment.getProperty("spring.ai.dashscope.api-key", ""))) {
            return new ReadinessCheckVO(key, "failed", label + "已启用，但 DashScope API Key 缺失。");
        }
        if (!beanAvailable) {
            return new ReadinessCheckVO(key, "failed", label + "已启用，但运行时 Bean 不存在。");
        }
        return new ReadinessCheckVO(key, "ready", label + " Bean 与配置已就绪。");
    }

    private ReadinessCheckVO checkSkillsRoot() {
        return checkDirectory("skills_root", appProperties.getStorage().getSkillsRoot(), false);
    }

    private ReadinessCheckVO checkToolsWorkspace() {
        return checkDirectory("tools_workspace", appProperties.getStorage().getToolsWorkspaceRoot(), true);
    }

    private ReadinessCheckVO checkDirectory(String key, Path directory, boolean requireWritable) {
        Path normalized = directory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalized);
            if (!Files.isDirectory(normalized) || !Files.isReadable(normalized)) {
                return new ReadinessCheckVO(key, "failed", normalized + " 不可读。");
            }
            if (requireWritable) {
                Path probe = Files.createTempFile(normalized, "probe-", ".tmp");
                Files.deleteIfExists(probe);
            }
            return new ReadinessCheckVO(key, "ready", normalized + (requireWritable ? " 可读可写。" : " 可读。"));
        } catch (IOException exception) {
            return new ReadinessCheckVO(key, "failed", normalized + " 检查失败: " + exception.getMessage());
        }
    }
}
