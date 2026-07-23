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

class V29MigrationRecoveryIT {

    @Test
    void v29ConvergesWhenItsFirstColumnsAlreadyExist() throws Exception {
        String sourceUrl = setting("SPRING_DATASOURCE_URL",
                "jdbc:mysql://localhost:3306/teacher_cert"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                        + "&allowPublicKeyRetrieval=true&useSSL=false");
        String username = setting("SPRING_DATASOURCE_USERNAME", "root");
        String password = setting("SPRING_DATASOURCE_PASSWORD", "root123");
        String schema = "teacher_cert_v29_" + UUID.randomUUID().toString().replace("-", "");
        String recoveryUrl = replaceDatabase(sourceUrl, schema);

        try (Connection admin = DriverManager.getConnection(sourceUrl, username, password);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        try {
            Flyway.configure()
                    .dataSource(recoveryUrl, username, password)
                    .locations("classpath:db/migration")
                    .target("28")
                    .load()
                    .migrate();

            // 模拟 MySQL 非事务 DDL 在 V29 前半已提交、后续语句失败的安全恢复起点。
            try (Connection connection = DriverManager.getConnection(recoveryUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE file_object
                        ADD COLUMN checksum_algorithm VARCHAR(32) DEFAULT NULL
                        COMMENT '服务端内容摘要算法；视频使用SHA256_TREE_V1' AFTER upload_metadata_hash
                        """);
                statement.execute("""
                        ALTER TABLE file_object
                        ADD COLUMN content_hash_verified TINYINT NOT NULL DEFAULT 0
                        COMMENT '摘要是否由服务端读取对象内容后验证' AFTER checksum_algorithm
                        """);
            }

            Flyway.configure()
                    .dataSource(recoveryUrl, username, password)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(recoveryUrl, username, password)) {
                for (String column : new String[]{
                        "checksum_algorithm", "content_hash_verified", "media_codec",
                        "media_validation_policy_hash", "media_probe_version"}) {
                    assertThat(count(connection, """
                            SELECT COUNT(*) FROM information_schema.columns
                            WHERE table_schema = ? AND table_name = 'file_object' AND column_name = ?
                            """, schema, column)).isEqualTo(1);
                }
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM information_schema.statistics
                        WHERE table_schema = ? AND table_name = 'file_object'
                          AND index_name = 'idx_file_verified_hash'
                        """, schema)).isGreaterThan(0);
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM sys_param
                        WHERE param_key IN ('video.allowedCodecs', 'video.timelineToleranceSeconds')
                          AND deleted = 0
                        """)).isEqualTo(2);
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '29' AND success = 1
                        """)).isEqualTo(1);
            }
        } finally {
            if (schema.toLowerCase(Locale.ROOT).startsWith("teacher_cert_v29_")) {
                try (Connection admin = DriverManager.getConnection(sourceUrl, username, password);
                     Statement statement = admin.createStatement()) {
                    statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
                }
            }
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
