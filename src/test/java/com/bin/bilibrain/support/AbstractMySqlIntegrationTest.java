package com.bin.bilibrain.support;

import com.bin.bilibrain.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractMySqlIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("bilibrain_test")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppProperties appProperties;

    @BeforeEach
    void resetDatabaseState() {
        if (!shouldResetState()) {
            return;
        }
        jdbcTemplate.execute("DELETE FROM GRAPH_CHECKPOINT");
        jdbcTemplate.execute("DELETE FROM GRAPH_THREAD");
        jdbcTemplate.execute("DELETE FROM tool_calls");
        jdbcTemplate.execute("DELETE FROM tool_workspaces");
        jdbcTemplate.execute("DELETE FROM skill_activations");
        jdbcTemplate.execute("DELETE FROM chat_messages");
        jdbcTemplate.execute("DELETE FROM chat_conversation_context_stats");
        jdbcTemplate.execute("DELETE FROM chat_conversation_memory");
        jdbcTemplate.execute("DELETE FROM chat_conversations");
        jdbcTemplate.execute("DELETE FROM ingestion_tasks");
        jdbcTemplate.execute("DELETE FROM video_pipeline");
        jdbcTemplate.execute("DELETE FROM video_summaries");
        jdbcTemplate.execute("DELETE FROM transcripts");
        jdbcTemplate.execute("DELETE FROM app_state");
        jdbcTemplate.execute("DELETE FROM videos");
        jdbcTemplate.execute("DELETE FROM folders");
        jdbcTemplate.execute("DELETE FROM processing_settings");
        resetDirectory(appProperties.getStorage().getAudioDir());
        resetDirectory(appProperties.getStorage().getUploadDir());
        resetDirectory(appProperties.getStorage().getToolsWorkspaceRoot());
        resetDirectory(appProperties.getStorage().getSkillsRoot());
    }

    private void resetDirectory(Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        try {
            if (Files.exists(normalized)) {
                try (var walk = Files.walk(normalized)) {
                    walk.sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(normalized))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException exception) {
                                throw new IllegalStateException("清理测试目录失败。", exception);
                            }
                        });
                }
            }
            Files.createDirectories(normalized);
        } catch (IOException exception) {
            throw new IllegalStateException("清理测试目录失败。", exception);
        }
    }

    protected boolean shouldResetState() {
        return true;
    }
}
