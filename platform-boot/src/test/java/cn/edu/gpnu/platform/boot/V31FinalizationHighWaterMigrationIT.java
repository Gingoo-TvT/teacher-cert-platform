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

class V31FinalizationHighWaterMigrationIT {

    @Test
    void v31BackfillsNullAndPreservesExistingPositiveGeneration() throws Exception {
        String sourceUrl = setting("SPRING_DATASOURCE_URL",
                "jdbc:mysql://localhost:3306/teacher_cert"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                        + "&allowPublicKeyRetrieval=true&useSSL=false");
        String username = setting("SPRING_DATASOURCE_USERNAME", "root");
        String password = setting("SPRING_DATASOURCE_PASSWORD", "root123");
        String schema = "teacher_cert_v31_" + UUID.randomUUID().toString().replace("-", "");
        String migrationUrl = replaceDatabase(sourceUrl, schema);

        try (Connection admin = DriverManager.getConnection(sourceUrl, username, password);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        try {
            Flyway.configure()
                    .dataSource(migrationUrl, username, password)
                    .locations("classpath:db/migration")
                    .target("30")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(migrationUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO video_upload_session
                            (id, upload_id, student_id, college_id, assessment_year,
                             file_md5, file_name, file_size, chunk_size, total_chunks,
                             status, finalization_token, created_at, updated_at, deleted)
                        VALUES
                            (310000000000000001, 'v31-null', 1, 1, '2026',
                             'null-token', 'null.mp4', 1, 1, 1,
                             'UPLOADING', NULL, NOW(), NOW(), 0),
                            (310000000000000002, 'v31-positive', 2, 1, '2026',
                             'positive-token', 'positive.mp4', 1, 1, 1,
                             'MERGING', 42, NOW(), NOW(), 0)
                        """);
            }

            Flyway.configure()
                    .dataSource(migrationUrl, username, password)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(migrationUrl, username, password)) {
                assertThat(value(connection,
                        "SELECT finalization_token FROM video_upload_session WHERE upload_id = 'v31-null'"))
                        .isEqualTo(0L);
                assertThat(value(connection,
                        "SELECT finalization_token FROM video_upload_session WHERE upload_id = 'v31-positive'"))
                        .isEqualTo(42L);
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'video_upload_session'
                          AND column_name = 'finalization_token'
                          AND is_nullable = 'NO' AND column_default = '0'
                        """, schema)).isEqualTo(1L);
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '31' AND success = 1
                        """)).isEqualTo(1L);
            }
        } finally {
            if (schema.toLowerCase(Locale.ROOT).startsWith("teacher_cert_v31_")) {
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
        return environmentValue == null || environmentValue.isBlank() ? defaultValue : environmentValue;
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
