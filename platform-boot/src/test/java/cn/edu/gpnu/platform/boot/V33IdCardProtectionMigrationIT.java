package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.boot.config.IdCardBackfillFlywayCallback;
import cn.edu.gpnu.platform.security.config.IdCardProtectionProperties;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class V33IdCardProtectionMigrationIT {

    private static final String ID_CARD_NO = "11010119900628002X";
    private static final String TEST_KEY =
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String TEST_PEPPER =
            "ws8-integration-only-hmac-pepper-at-least-32-bytes";

    @Test
    void v33ProtectsLegacyRowsAndExchangeHistoryIdempotently() throws Exception {
        withScratchSchema((url, username, password, schema) -> {
            migrateToV32(url, username, password);
            try (Connection connection = DriverManager.getConnection(url, username, password);
                 Statement statement = connection.createStatement()) {
                seedLegacyStudents(statement);
                seedLegacyHistory(statement);
                statement.execute("UPDATE student SET deleted = 1 WHERE id = 9002");
            }

            IdCardProtectionService protection = protectionService();
            migrateWithProtection(url, username, password, protection);

            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                assertProtectedState(connection, protection);
                String firstStudentCipher = value(connection,
                        "SELECT id_card_no FROM student WHERE id = 9001");
                String firstPreview = value(connection,
                        "SELECT CAST(preview_json AS CHAR) FROM import_export_batch "
                                + "WHERE batch_no = 'WS8-MIGRATE'");
                String firstScope = value(connection,
                        "SELECT CAST(scope_json AS CHAR) FROM import_export_batch "
                                + "WHERE batch_no = 'WS8-MIGRATE'");

                migrateWithProtection(url, username, password, protection);

                assertThat(value(connection,
                        "SELECT id_card_no FROM student WHERE id = 9001"))
                        .isEqualTo(firstStudentCipher);
                assertThat(value(connection,
                        "SELECT CAST(preview_json AS CHAR) FROM import_export_batch "
                                + "WHERE batch_no = 'WS8-MIGRATE'"))
                        .isEqualTo(firstPreview);
                assertThat(value(connection,
                        "SELECT CAST(scope_json AS CHAR) FROM import_export_batch "
                                + "WHERE batch_no = 'WS8-MIGRATE'"))
                        .isEqualTo(firstScope);
            }
        });
    }

    @Test
    void tamperedV1HistoryRollsBackBackfillAndCanRetryWithoutRepair() throws Exception {
        withScratchSchema((url, username, password, schema) -> {
            migrateToV32(url, username, password);
            try (Connection connection = DriverManager.getConnection(url, username, password);
                 Statement statement = connection.createStatement()) {
                seedLegacyStudents(statement);
                statement.execute("""
                        INSERT INTO import_export_batch
                            (id, batch_no, type, total, success_count, fail_count,
                             preview_json, status, deleted)
                        VALUES
                            (833000000000000011, 'WS8-TAMPER', 'import', 1, 1, 0,
                             JSON_OBJECT('idCardNo', 'v1:tampered'), 'PREVALIDATED', 0)
                        """);
            }

            IdCardProtectionService protection = protectionService();
            assertThatThrownBy(() -> migrateWithProtection(url, username, password, protection))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("WS-8");

            try (Connection connection = DriverManager.getConnection(url, username, password);
                 Statement statement = connection.createStatement()) {
                assertThat(value(connection,
                        "SELECT id_card_no FROM student WHERE id = 9001"))
                        .isEqualTo(ID_CARD_NO);
                assertThat(value(connection,
                        "SELECT id_card_hmac FROM student WHERE id = 9001"))
                        .isNull();
                assertThat(value(connection, """
                        SELECT version FROM flyway_schema_history
                        WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1
                        """)).isEqualTo("33");
                assertThat(count(connection, """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'student'
                          AND column_name = 'idcard_key'
                        """, connection.getCatalog())).isZero();
                statement.execute("""
                        UPDATE import_export_batch
                        SET preview_json = JSON_OBJECT('idCardNo', '11010119900628002X')
                        WHERE batch_no = 'WS8-TAMPER'
                        """);
            }

            migrateWithProtection(url, username, password, protection);
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                String stored = value(connection,
                        "SELECT id_card_no FROM student WHERE id = 9001");
                assertThat(protection.isEncrypted(stored)).isTrue();
                assertThat(protection.decrypt(stored)).isEqualTo(ID_CARD_NO);
                assertThat(value(connection,
                        "SELECT id_card_hmac FROM student WHERE id = 9001"))
                        .isEqualTo(protection.hmac(ID_CARD_NO));
            }
        });
    }

    @Test
    void normalizedHmacCollisionFailsBeforeAnyProtectedDataUpdate() throws Exception {
        withScratchSchema((url, username, password, schema) -> {
            migrateToV32(url, username, password);
            try (Connection connection = DriverManager.getConnection(url, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO student
                            (id, student_no, name, gender, id_card_type, id_card_no,
                             birth_date, identity_type, college_id, status, locked, deleted)
                        VALUES
                            (9001, 'WS8-COLLISION-1', 'WS8冲突学生一', 'female',
                             'resident_id_card', ' 11010119900628002X', '1990/6/28',
                             'normal_student', 800000000000000201, 'DRAFT', 0, 0),
                            (9002, 'WS8-COLLISION-2', 'WS8冲突学生二', 'male',
                             'resident_id_card', '11010119900628002X', '1990/6/28',
                             'normal_student', 800000000000000202, 'DRAFT', 0, 0)
                        """);
                seedLegacyHistory(statement);
            }

            IdCardProtectionService protection = spy(protectionService());
            assertThatThrownBy(() -> migrateWithProtection(
                    url, username, password, protection))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("WS-8");
            verify(protection, never()).encrypt(anyString());

            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                assertThat(value(connection,
                        "SELECT id_card_no FROM student WHERE id = 9001"))
                        .isEqualTo(" 11010119900628002X");
                assertThat(value(connection,
                        "SELECT id_card_no FROM student WHERE id = 9002"))
                        .isEqualTo(ID_CARD_NO);
                assertThat(value(connection,
                        "SELECT id_card_hmac FROM student WHERE id = 9001"))
                        .isNull();
                assertThat(value(connection,
                        "SELECT id_card_hmac FROM student WHERE id = 9002"))
                        .isNull();
                assertThat(value(connection,
                        "SELECT id_card_no FROM certificate WHERE id = 833000000000000001"))
                        .isEqualTo(ID_CARD_NO);
                assertThat(value(connection,
                        "SELECT id_card_hmac FROM certificate WHERE id = 833000000000000001"))
                        .isNull();
                assertThat(value(connection, """
                        SELECT JSON_UNQUOTE(JSON_EXTRACT(preview_json, '$.idCardNo'))
                        FROM import_export_batch WHERE batch_no = 'WS8-MIGRATE'
                        """)).isEqualTo(ID_CARD_NO);
                assertThat(value(connection, """
                        SELECT JSON_UNQUOTE(JSON_EXTRACT(before_json, '$.idCardHmac'))
                        FROM import_record_ref WHERE batch_no = 'WS8-MIGRATE'
                        """)).isNull();
            }
        });
    }

    private void assertProtectedState(Connection connection, IdCardProtectionService protection)
            throws Exception {
        String studentCipher = value(connection,
                "SELECT id_card_no FROM student WHERE id = 9001");
        String certificateCipher = value(connection,
                "SELECT id_card_no FROM certificate WHERE id = 833000000000000001");
        assertThat(studentCipher).isNotEqualTo(certificateCipher);
        assertThat(protection.decrypt(studentCipher)).isEqualTo(ID_CARD_NO);
        assertThat(protection.decrypt(certificateCipher)).isEqualTo(ID_CARD_NO);
        assertThat(value(connection,
                "SELECT id_card_hmac FROM student WHERE id = 9001"))
                .isEqualTo(protection.hmac(ID_CARD_NO));
        assertThat(value(connection,
                "SELECT id_card_hmac FROM certificate WHERE id = 833000000000000001"))
                .isEqualTo(protection.hmac(ID_CARD_NO));
        assertThat(value(connection,
                "SELECT id_card_hmac FROM student WHERE id = 9002"))
                .isNull();
        assertThat(count(connection, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = ? AND table_name = 'student' AND column_name = 'idcard_key'
                """, connection.getCatalog())).isZero();

        for (String sql : new String[]{
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(preview_json, '$.idCardNo')) "
                        + "FROM import_export_batch WHERE batch_no = 'WS8-MIGRATE'",
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(scope_json, '$.idCardNo')) "
                        + "FROM import_export_batch WHERE batch_no = 'WS8-MIGRATE'",
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(before_json, '$.idCardNo')) "
                        + "FROM import_record_ref WHERE batch_no = 'WS8-MIGRATE'",
                "SELECT error_value FROM import_error_detail WHERE batch_no = 'WS8-MIGRATE'"}) {
            String stored = value(connection, sql);
            assertThat(protection.isEncrypted(stored)).isTrue();
            assertThat(protection.decrypt(stored)).isEqualTo(ID_CARD_NO);
        }
        String protectedKeyword = value(connection,
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(scope_json, '$.keyword')) "
                        + "FROM import_export_batch WHERE batch_no = 'WS8-MIGRATE'");
        assertThat(protection.isEncrypted(protectedKeyword)).isTrue();
        assertThat(protection.decrypt(protectedKeyword)).isEqualTo("P98765432");
        assertThat(value(connection, """
                SELECT JSON_UNQUOTE(JSON_EXTRACT(before_json, '$.idCardHmac'))
                FROM import_record_ref WHERE batch_no = 'WS8-MIGRATE'
                """)).isEqualTo(protection.hmac(ID_CARD_NO));
    }

    private void seedLegacyHistory(Statement statement) throws Exception {
        statement.execute("""
                INSERT INTO certificate
                    (id, student_id, college_id, assessment_year, cert_no, student_no,
                     student_name, id_card_type, id_card_no, status, deleted)
                VALUES
                    (833000000000000001, 9001, 800000000000000201, '2026',
                     '202610588344499991', 'S00123', '学生测试账号',
                     'resident_id_card', '11010119900628002X', 'GENERATED', 0)
                """);
        statement.execute("""
                INSERT INTO import_export_batch
                    (id, batch_no, type, total, success_count, fail_count,
                     scope_json, preview_json, status, deleted)
                VALUES
                    (833000000000000002, 'WS8-MIGRATE', 'import', 1, 1, 0,
                     JSON_OBJECT('idCardNo', '11010119900628002X',
                                 'keyword', 'P98765432'),
                     JSON_OBJECT('idCardNo', '11010119900628002X'),
                     'PREVALIDATED', 0)
                """);
        statement.execute("""
                INSERT INTO import_record_ref
                    (id, batch_id, batch_no, table_name, record_id, action,
                     before_json, after_json, deleted)
                VALUES
                    (833000000000000003, 833000000000000002, 'WS8-MIGRATE',
                     'student', 9001, 'UPDATE',
                     JSON_OBJECT('idCardType', 'resident_id_card',
                                 'idCardNo', '11010119900628002X'),
                     JSON_OBJECT('idCardType', 'resident_id_card',
                                 'idCardNo', '11010119900628002X'), 0)
                """);
        statement.execute("""
                INSERT INTO import_error_detail
                    (id, batch_id, batch_no, row_no, field_name, error_value,
                     error_reason, deleted)
                VALUES
                    (833000000000000004, 833000000000000002, 'WS8-MIGRATE', 2,
                     '身份证件号码', '11010119900628002X', '测试错误', 0)
                """);
    }

    private void seedLegacyStudents(Statement statement) throws Exception {
        statement.execute("""
                INSERT INTO student
                    (id, student_no, name, gender, id_card_type, id_card_no,
                     birth_date, identity_type, college_id, status, locked, deleted)
                VALUES
                    (9001, 'WS8-LEGACY-1', 'WS8旧学生一', 'female',
                     'resident_id_card', '11010119900628002X', '1990/6/28',
                     'normal_student', 800000000000000201, 'DRAFT', 0, 0),
                    (9002, 'WS8-LEGACY-2', 'WS8旧学生二', 'male',
                     'resident_id_card', '110101199107010019', '1991/7/1',
                     'normal_student', 800000000000000202, 'DRAFT', 0, 0)
                """);
    }

    private void migrateToV32(String url, String username, String password) {
        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .target("32")
                .load()
                .migrate();
    }

    private void migrateWithProtection(
            String url, String username, String password, IdCardProtectionService protection) {
        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .callbacks(new IdCardBackfillFlywayCallback(protection, new ObjectMapper()))
                .load()
                .migrate();
    }

    private IdCardProtectionService protectionService() {
        IdCardProtectionProperties properties = new IdCardProtectionProperties();
        properties.setEncryptionKey(TEST_KEY);
        properties.setHmacPepper(TEST_PEPPER);
        return new IdCardProtectionService(properties);
    }

    private String value(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
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

    private void withScratchSchema(ScratchAction action) throws Exception {
        String sourceUrl = setting("SPRING_DATASOURCE_URL",
                "jdbc:mysql://localhost:3306/teacher_cert"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                        + "&allowPublicKeyRetrieval=true&useSSL=false");
        String username = setting("SPRING_DATASOURCE_USERNAME", "root");
        String password = setting("SPRING_DATASOURCE_PASSWORD", "root123");
        String schema = "teacher_cert_v33_" + UUID.randomUUID().toString().replace("-", "");
        String url = replaceDatabase(sourceUrl, schema);
        try (Connection admin = DriverManager.getConnection(sourceUrl, username, password);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        try {
            action.run(url, username, password, schema);
        } finally {
            if (schema.toLowerCase(Locale.ROOT).startsWith("teacher_cert_v33_")) {
                try (Connection admin = DriverManager.getConnection(sourceUrl, username, password);
                     Statement statement = admin.createStatement()) {
                    statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
                }
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

    @FunctionalInterface
    private interface ScratchAction {
        void run(String url, String username, String password, String schema) throws Exception;
    }
}
