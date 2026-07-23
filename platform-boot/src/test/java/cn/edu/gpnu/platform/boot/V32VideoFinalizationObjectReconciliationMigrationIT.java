package cn.edu.gpnu.platform.boot;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class V32VideoFinalizationObjectReconciliationMigrationIT {

    @Test
    void v32CreatesCandidateLedgerIndexesAndIdempotentParameters() throws Exception {
        String sourceUrl = setting("SPRING_DATASOURCE_URL",
                "jdbc:mysql://localhost:3306/teacher_cert"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                        + "&allowPublicKeyRetrieval=true&useSSL=false");
        String username = setting("SPRING_DATASOURCE_USERNAME", "root");
        String password = setting("SPRING_DATASOURCE_PASSWORD", "root123");
        String schema = "teacher_cert_v32_" + UUID.randomUUID().toString().replace("-", "");
        String migrationUrl = replaceDatabase(sourceUrl, schema);
        long existingParameterId = 329000000000000001L;

        try (Connection admin = DriverManager.getConnection(sourceUrl, username, password);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        try {
            Flyway.configure()
                    .dataSource(migrationUrl, username, password)
                    .locations("classpath:db/migration")
                    .target("31")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(migrationUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO sys_param
                            (id, param_key, param_value, param_type, param_group, description,
                             editable, created_by, created_at, updated_by, updated_at, deleted)
                        VALUES
                            (329000000000000001, 'video.finalizationCleanupSafetySeconds',
                             '5', 'int', 'video', 'V32 upgrade fixture',
                             1, 0, NOW(), 0, NOW(), 1)
                        """);
            }

            Flyway.configure()
                    .dataSource(migrationUrl, username, password)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(migrationUrl, username, password)) {
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = ?
                          AND table_name = 'video_finalization_object_candidate'
                        """, schema)).isEqualTo(1L);
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = ?
                          AND table_name = 'video_finalization_object_candidate'
                          AND (
                            (column_name = 'upload_id' AND data_type = 'varchar' AND is_nullable = 'NO')
                            OR (column_name = 'finalization_generation'
                                AND data_type = 'bigint' AND is_nullable = 'NO')
                            OR (column_name = 'upload_mode'
                                AND data_type = 'varchar' AND is_nullable = 'NO')
                            OR (column_name = 'bucket'
                                AND data_type = 'varchar' AND is_nullable = 'NO')
                            OR (column_name = 'object_key'
                                AND data_type = 'varchar' AND is_nullable = 'NO')
                            OR (column_name = 'state'
                                AND data_type = 'varchar' AND is_nullable = 'NO')
                            OR (column_name = 'cleanup_not_before' AND data_type = 'datetime')
                            OR (column_name = 'next_retry_at' AND data_type = 'datetime')
                            OR (column_name = 'claim_expires_at' AND data_type = 'datetime')
                            OR (column_name = 'attempt_count'
                                AND data_type = 'int' AND is_nullable = 'NO'
                                AND column_default = '0')
                            OR (column_name = 'registered_file_id' AND data_type = 'bigint')
                            OR (column_name = 'cleaned_at' AND data_type = 'datetime')
                            OR (column_name = 'deleted'
                                AND data_type = 'tinyint' AND is_nullable = 'NO'
                                AND column_default = '0')
                          )
                        """, schema)).isEqualTo(13L);
                assertThat(count(connection, """
                        SELECT COUNT(DISTINCT index_name)
                        FROM information_schema.statistics
                        WHERE table_schema = ?
                          AND table_name = 'video_finalization_object_candidate'
                          AND index_name IN (
                            'uk_video_final_candidate_generation',
                            'idx_video_final_candidate_due',
                            'idx_video_final_candidate_object',
                            'idx_video_final_candidate_upload'
                          )
                        """, schema)).isEqualTo(4L);
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM sys_param
                        WHERE deleted = 0 AND param_type = 'int' AND (
                          (param_key = 'video.finalizationCleanupSafetySeconds' AND param_value = '60')
                          OR (param_key = 'video.finalizationCleanupRetrySeconds' AND param_value = '60')
                          OR (param_key = 'video.finalizationCleanupClaimSeconds' AND param_value = '1860')
                          OR (param_key = 'video.finalizationCleanupBatchSize' AND param_value = '100')
                          OR (param_key = 'video.finalizationCleanupTombstoneCheckSeconds'
                              AND param_value = '3600')
                        )
                        """)).isEqualTo(5L);
                assertThat(value(connection, """
                        SELECT id FROM sys_param
                        WHERE param_key = 'video.finalizationCleanupSafetySeconds'
                        """)).isEqualTo(existingParameterId);
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '32' AND success = 1
                        """)).isEqualTo(1L);
            }
        } finally {
            if (schema.toLowerCase(Locale.ROOT).startsWith("teacher_cert_v32_")) {
                try (Connection admin = DriverManager.getConnection(sourceUrl, username, password);
                     Statement statement = admin.createStatement()) {
                    statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
                }
            }
        }
    }

    private long value(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private long count(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private String setting(String name, String defaultValue) {
        String systemValue = System.getProperty(name);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        String environmentValue = System.getenv(name);
        return environmentValue == null || environmentValue.isBlank()
                ? defaultValue : environmentValue;
    }

    private String replaceDatabase(String jdbcUrl, String schema) {
        int queryIndex = jdbcUrl.indexOf('?');
        String withoutQuery = queryIndex < 0 ? jdbcUrl : jdbcUrl.substring(0, queryIndex);
        String query = queryIndex < 0 ? "" : jdbcUrl.substring(queryIndex);
        int slash = withoutQuery.lastIndexOf('/');
        if (slash < "jdbc:mysql://".length()) {
            throw new IllegalArgumentException("无法识别MySQL JDBC URL");
        }
        return withoutQuery.substring(0, slash + 1) + schema + query;
    }
}
