package com.bin.bilibrain.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOn("dataSourceScriptDatabaseInitializer")
@RequiredArgsConstructor
public class AppStateSchemaBootstrap implements InitializingBean {
    private static final String TABLE_NAME = "app_state";
    private static final String COLUMN_NAME = "state_value";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void afterPropertiesSet() {
        String dataType = jdbcTemplate.query(
            """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
            resultSet -> resultSet.next() ? resultSet.getString("data_type") : null,
            TABLE_NAME,
            COLUMN_NAME
        );
        if (dataType == null || "longtext".equalsIgnoreCase(dataType)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " MODIFY COLUMN " + COLUMN_NAME + " LONGTEXT NOT NULL");
    }
}
