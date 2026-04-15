package com.bin.bilibrain.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@DependsOn("dataSourceScriptDatabaseInitializer")
@RequiredArgsConstructor
public class ChatMessageSchemaBootstrap implements InitializingBean {
    private static final String TABLE_NAME = "chat_messages";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void afterPropertiesSet() {
        Map<String, String> requiredColumns = new LinkedHashMap<>();
        requiredColumns.put("reasoning_text", "LONGTEXT");
        requiredColumns.put("agent_status", "VARCHAR(255)");
        requiredColumns.put("skill_events_json", "LONGTEXT");
        requiredColumns.put("tool_events_json", "LONGTEXT");
        requiredColumns.put("active_skills_json", "LONGTEXT");
        requiredColumns.put("approval_json", "LONGTEXT");

        requiredColumns.forEach(this::ensureColumn);
    }

    private void ensureColumn(String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
            Integer.class,
            TABLE_NAME,
            columnName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + columnName + " " + columnDefinition);
    }
}
