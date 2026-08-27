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

class V35VideoReviewerCountSnapshotMigrationIT {

    @Test
    void v35KeepsSubmittedSingleReviewerAndNormalizesLegacyLowParameter() throws Exception {
        String sourceUrl = setting("SPRING_DATASOURCE_URL",
                "jdbc:mysql://localhost:3306/teacher_cert"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                        + "&allowPublicKeyRetrieval=true&useSSL=false");
        String username = setting("SPRING_DATASOURCE_USERNAME", "root");
        String password = setting("SPRING_DATASOURCE_PASSWORD", "root123");
        String schema = "teacher_cert_v35_single_" + UUID.randomUUID().toString().replace("-", "");
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
                    .target("34")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(migrationUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("UPDATE sys_param SET param_value = '1' "
                        + "WHERE param_key = 'video.reviewerCount' AND deleted = 0");
                statement.execute("""
                        INSERT INTO video_review
                            (id, student_id, college_id, assessment_year, status,
                             created_at, updated_at, deleted)
                        VALUES
                            (350000000000000003, 3, 1, 'V35-SINGLE', 'REVIEWING', NOW(), NOW(), 0)
                        """);
                statement.execute("""
                        INSERT INTO video_review_task
                            (id, video_review_id, student_id, college_id, reviewer_id,
                             reviewer_role, score, conclusion, submitted, submit_time,
                             created_at, updated_at, deleted)
                        VALUES
                            (350000000000000014, 350000000000000003, 3, 1, 14,
                             'REVIEWER', 85, 'PASS', 1, NOW(), NOW(), NOW(), 0)
                        """);
            }

            Flyway.configure()
                    .dataSource(migrationUrl, username, password)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(migrationUrl, username, password)) {
                assertThat(value(connection,
                        "SELECT reviewer_count FROM video_review WHERE id = 350000000000000003"))
                        .isEqualTo(2L);
                assertThat(value(connection,
                        "SELECT reviewer_id FROM video_review_task WHERE id = 350000000000000014"))
                        .isEqualTo(14L);
                assertThat(value(connection,
                        "SELECT submitted FROM video_review_task WHERE id = 350000000000000014"))
                        .isEqualTo(1L);
                assertThat(value(connection,
                        "SELECT score FROM video_review_task WHERE id = 350000000000000014"))
                        .isEqualTo(85L);
                assertThat(value(connection, """
                        SELECT CAST(param_value AS UNSIGNED) FROM sys_param
                        WHERE param_key = 'video.reviewerCount' AND deleted = 0
                        """)).isEqualTo(2L);
            }
        } finally {
            if (schema.toLowerCase(Locale.ROOT).startsWith("teacher_cert_v35_single_")) {
                try (Connection admin = DriverManager.getConnection(sourceUrl, username, password);
                     Statement statement = admin.createStatement()) {
                    statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
                }
            }
        }
    }

    @Test
    void v35FreezesLegacyElevenBeforeNormalizingCurrentParameter() throws Exception {
        String sourceUrl = setting("SPRING_DATASOURCE_URL",
                "jdbc:mysql://localhost:3306/teacher_cert"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                        + "&allowPublicKeyRetrieval=true&useSSL=false");
        String username = setting("SPRING_DATASOURCE_USERNAME", "root");
        String password = setting("SPRING_DATASOURCE_PASSWORD", "root123");
        String schema = "teacher_cert_v35_" + UUID.randomUUID().toString().replace("-", "");
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
                    .target("34")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(migrationUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("UPDATE sys_param SET param_value = '11' "
                        + "WHERE param_key = 'video.reviewerCount' AND deleted = 0");
                statement.execute("""
                        INSERT INTO video_review
                            (id, student_id, college_id, assessment_year, status,
                             created_at, updated_at, deleted)
                        VALUES
                            (350000000000000001, 1, 1, 'V35-A', 'REVIEWING', NOW(), NOW(), 0),
                            (350000000000000002, 2, 1, 'V35-B', 'WAIT_REVIEW', NOW(), NOW(), 0),
                            (350000000000000004, 4, 1, 'V35-ELEVEN', 'REVIEWING', NOW(), NOW(), 0)
                        """);
                statement.execute("""
                        INSERT INTO video_review_task
                            (id, video_review_id, student_id, college_id, reviewer_id,
                             reviewer_role, submitted, created_at, updated_at, deleted)
                        VALUES
                            (350000000000000011, 350000000000000001, 1, 1, 11,
                             'REVIEWER', 1, NOW(), NOW(), 0),
                            (350000000000000012, 350000000000000001, 1, 1, 12,
                             'REVIEWER', 0, NOW(), NOW(), 0),
                            (350000000000000013, 350000000000000001, 1, 1, 13,
                             'REVIEWER', 0, NOW(), NOW(), 1)
                        """);
                for (int index = 1; index <= 11; index++) {
                    long taskId = 350000000000000100L + index;
                    long reviewerId = 100L + index;
                    String submittedValues = index <= 3
                            ? "80, 'PASS', 1, NOW()"
                            : "NULL, NULL, 0, NULL";
                    statement.execute("""
                            INSERT INTO video_review_task
                                (id, video_review_id, student_id, college_id, reviewer_id,
                                 reviewer_role, score, conclusion, submitted, submit_time,
                                 created_at, updated_at, deleted)
                            VALUES
                            """ + "(" + taskId + ", 350000000000000004, 4, 1, " + reviewerId
                            + ", 'REVIEWER', " + submittedValues + ", NOW(), NOW(), 0)");
                }
            }

            Flyway.configure()
                    .dataSource(migrationUrl, username, password)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(migrationUrl, username, password)) {
                assertThat(value(connection,
                        "SELECT reviewer_count FROM video_review WHERE id = 350000000000000001"))
                        .isEqualTo(2L);
                assertThat(value(connection,
                        "SELECT reviewer_count FROM video_review WHERE id = 350000000000000002"))
                        .isEqualTo(11L);
                assertThat(value(connection,
                        "SELECT reviewer_count FROM video_review WHERE id = 350000000000000004"))
                        .isEqualTo(11L);
                assertThat(value(connection, """
                        SELECT CAST(param_value AS UNSIGNED) FROM sys_param
                        WHERE param_key = 'video.reviewerCount' AND deleted = 0
                        """)).isEqualTo(10L);
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM video_review_task
                        WHERE video_review_id = 350000000000000004
                          AND reviewer_role = 'REVIEWER' AND deleted = 0
                        """)).isEqualTo(11L);
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM video_review_task
                        WHERE video_review_id = 350000000000000004
                          AND submitted = 1 AND score = 80 AND conclusion = 'PASS' AND deleted = 0
                        """)).isEqualTo(3L);
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'video_review'
                          AND column_name = 'reviewer_count' AND is_nullable = 'NO'
                          AND column_default = '2'
                        """, schema)).isEqualTo(1L);
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '35' AND success = 1
                        """)).isEqualTo(1L);
            }
        } finally {
            if (schema.toLowerCase(Locale.ROOT).startsWith("teacher_cert_v35_")) {
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
