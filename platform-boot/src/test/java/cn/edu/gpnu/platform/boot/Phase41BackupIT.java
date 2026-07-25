package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.system.entity.BackupRecord;
import cn.edu.gpnu.platform.system.service.impl.DatabaseBackupService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 41 整改：真实生成整份 gzip，在含 Flyway 生产种子的独立恢复 schema 回放两次，
 * 并对 37 张受管表做逐行全字段指纹核对。两个 schema 与 MinIO 对象均由本测试独占并清理。
 */
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = PlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Phase41BackupIT {

    private static final Pattern TABLE_SECTION =
            Pattern.compile("(?m)^-- ---------- ([a-z0-9_]+) ----------\\r?$");
    private static final String BASE_URL = setting(
            "SPRING_DATASOURCE_URL",
            "jdbc:mysql://localhost:3306/teacher_cert"
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true&useSSL=false");
    private static final String USERNAME =
            setting("SPRING_DATASOURCE_USERNAME", "root");
    private static final String PASSWORD =
            setting("SPRING_DATASOURCE_PASSWORD", "root123");
    private static final ScratchSchemas SCRATCH = prepareScratchSchemas();

    private static final long COLLEGE_ID = 341000000000000001L;
    private static final long MAJOR_ID = 341000000000000002L;
    private static final long NOTIFICATION_ID = 341000000000000003L;
    private static final long RESTORE_SENTINEL_ID = 341000000000000004L;
    private static final long COMPLETED_BACKUP_ID = 341000000000000005L;
    private static final long RUNNING_BACKUP_ID = 341000000000000006L;
    private static final long PENDING_BACKUP_ID = 341000000000000007L;
    private static final long FAILED_BACKUP_ID = 341000000000000008L;
    private static final String SPECIAL_TEXT = "恢复快照：O'Neil 与反斜杠 \\\\ 保持原样\n第二行";

    @DynamicPropertySource
    static void scratchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SCRATCH::sourceUrl);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.data.redis.host",
                () -> setting("SPRING_DATA_REDIS_HOST", "localhost"));
        registry.add("spring.data.redis.port",
                () -> Integer.parseInt(setting("SPRING_DATA_REDIS_PORT", "6379")));
        registry.add("spring.data.redis.password",
                () -> setting("SPRING_DATA_REDIS_PASSWORD", ""));
        registry.add("minio.endpoint",
                () -> setting("MINIO_ENDPOINT", "http://localhost:9000"));
        registry.add("minio.access-key",
                () -> setting("MINIO_ACCESS_KEY", "minioadmin"));
        registry.add("minio.secret-key",
                () -> setting("MINIO_SECRET_KEY", "minioadmin123"));
        registry.add("minio.bucket",
                () -> setting("MINIO_BUCKET", "teacher-cert"));
        registry.add("platform.security.jwt.secret",
                () -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        registry.add("platform.security.initial-password", () -> "Staff-Initial-2026!");
        registry.add("platform.backup.schedule.enabled", () -> false);
        registry.add("platform.cleanup.schedule.enabled", () -> false);
        registry.add("platform.demo.enabled", () -> false);
    }

    @Autowired
    private DatabaseBackupService backupService;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private JdbcTemplate source;

    @Value("${platform.backup.bucket:${minio.bucket}}")
    private String bucket;

    @Test
    void fullArtifactReplacesFlywaySeedsAndRestoresExactSnapshotAtomically() throws Exception {
        insertSourceFixtures();
        Set<String> tables = tableNames(source);
        assertThat(tables).hasSize(37);
        Map<String, TableSnapshot> expected = backupArtifactSnapshots(source, tables);
        long expectedRows = expected.values().stream().mapToLong(TableSnapshot::rowCount).sum();

        BackupRecord backup = backupService.backup(
                "full", "P41\r\nRESTORE-DRILL", "Phase 41 整库恢复演练", 0L);
        String objectKey = objectKey(backup);
        try {
            byte[] gzipBytes;
            try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build())) {
                gzipBytes = in.readAllBytes();
            }
            var stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
            assertThat(stat.size()).isEqualTo(backup.getByteSize());
            assertThat(stat.userMetadata())
                    .containsEntry("tcp-record-id", backup.getId().toString())
                    .containsEntry("tcp-sha256", backup.getChecksum().substring("sha256:".length()))
                    .containsEntry("tcp-restore-mode", "replace-after-flyway")
                    .containsEntry("tcp-table-count", "37")
                    .containsEntry("tcp-row-count", Long.toString(expectedRows));
            assertThat("sha256:" + sha256(gzipBytes)).isEqualTo(backup.getChecksum());

            String sql;
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gzipBytes))) {
                sql = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertArtifactContract(sql, backup, tables, expectedRows);

            Flyway.configure()
                    .dataSource(SCRATCH.restoreUrl(), USERNAME, PASSWORD)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            JdbcTemplate restore = new JdbcTemplate(
                    new DriverManagerDataSource(SCRATCH.restoreUrl(), USERNAME, PASSWORD));

            Long seededPermissions = restore.queryForObject(
                    "SELECT COUNT(*) FROM sys_permission", Long.class);
            assertThat(seededPermissions).isNotNull().isGreaterThan(0L);
            int flywayRowsBefore = restore.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
            insertRestoreOnlySentinel(restore);

            replayArtifact(SCRATCH.restoreUrl(), sql);
            assertRestoredSnapshot(restore, tables, expected, backup.getId(), flywayRowsBefore);

            // 同一产物重复回放仍得到相同精确快照，不残留第一次恢复后的额外数据。
            insertRestoreOnlySentinel(restore);
            replayArtifact(SCRATCH.restoreUrl(), sql);
            assertRestoredSnapshot(restore, tables, expected, backup.getId(), flywayRowsBefore);

            // 任一 INSERT 失败时，前置 DELETE 与已写入行必须整事务回滚，不能留下半恢复库。
            Map<String, TableSnapshot> beforeFailure = snapshots(restore, tables);
            String broken = sql.replaceFirst(
                    Pattern.quote("INSERT INTO `sys_region`"),
                    Matcher.quoteReplacement("INSERT INTO `phase41_missing_table`"));
            assertThatThrownBy(() -> replayArtifact(SCRATCH.restoreUrl(), broken))
                    .isInstanceOf(Exception.class);
            assertThat(snapshots(restore, tables)).isEqualTo(beforeFailure);
        } finally {
            // 仅清理由本测试精确创建的对象；失败必须使测试失败，避免绿灯遗留测试资产。
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
        }
    }

    private void assertArtifactContract(
            String sql,
            BackupRecord backup,
            Set<String> tables,
            long expectedRows) {
        assertThat(backup.getStatus()).isEqualTo("COMPLETED");
        assertThat(backup.getTableCount()).isEqualTo(37);
        assertThat(backup.getRowCount()).isEqualTo(expectedRows);
        assertThat(sql).startsWith("-- Teacher-Cert-Platform");
        assertThat(sql).contains(
                "-- restoreMode=REPLACE_AFTER_FLYWAY transaction=SINGLE "
                        + "backupRecordPolicy=TERMINAL_ONLY");
        assertThat(sql).contains("-- backupType=full scope=P41  RESTORE-DRILL recordId=");
        assertThat(sql).doesNotContain("\nRESTORE-DRILL");
        assertThat(sql).contains("START TRANSACTION;");
        assertThat(sql).contains("DELETE FROM `sys_permission`;");
        assertThat(sql).contains("INSERT INTO `sys_permission`");
        assertThat(sql).contains("COMMIT;");
        assertThat(sql).contains("SET FOREIGN_KEY_CHECKS=@TCP_OLD_FOREIGN_KEY_CHECKS;");
        assertThat(tableSections(sql)).containsExactlyInAnyOrderElementsOf(tables);
    }

    private void assertRestoredSnapshot(
            JdbcTemplate restore,
            Set<String> tables,
            Map<String, TableSnapshot> expected,
            Long currentBackupId,
            int flywayRowsBefore) throws Exception {
        assertThat(snapshots(restore, tables)).isEqualTo(expected);
        assertThat(restore.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id = ? AND deleted = 1",
                Long.class, NOTIFICATION_ID)).isEqualTo(1L);
        assertThat(restore.queryForObject(
                "SELECT content FROM notification WHERE id = ?",
                String.class, NOTIFICATION_ID)).isEqualTo(SPECIAL_TEXT);
        assertThat(restore.queryForObject("""
                SELECT COUNT(*)
                FROM sys_major m
                JOIN sys_college c ON c.id = m.college_id
                WHERE m.id = ? AND c.id = ? AND m.deleted = 0 AND c.deleted = 0
                """, Long.class, MAJOR_ID, COLLEGE_ID)).isEqualTo(1L);
        assertThat(restore.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id = ?",
                Long.class, RESTORE_SENTINEL_ID)).isZero();
        assertThat(restore.queryForObject(
                "SELECT COUNT(*) FROM backup_record WHERE id = ?",
                Long.class, currentBackupId)).isZero();
        assertThat(restore.queryForObject(
                "SELECT COUNT(*) FROM backup_record WHERE id = ? AND status = 'COMPLETED'",
                Long.class, COMPLETED_BACKUP_ID)).isEqualTo(1L);
        assertThat(restore.queryForObject(
                "SELECT COUNT(*) FROM backup_record WHERE id = ? AND status = 'FAILED'",
                Long.class, FAILED_BACKUP_ID)).isEqualTo(1L);
        assertThat(restore.queryForObject(
                "SELECT COUNT(*) FROM backup_record WHERE id IN (?, ?)",
                Long.class, RUNNING_BACKUP_ID, PENDING_BACKUP_ID)).isZero();
        assertThat(restore.queryForObject(
                "SELECT COUNT(*) FROM backup_record WHERE status IN ('RUNNING','PENDING')",
                Long.class)).isZero();
        assertThat(restore.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class)).isEqualTo(flywayRowsBefore);
        assertThat(restore.queryForObject("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = 1 AND version IS NOT NULL
                ORDER BY installed_rank DESC
                LIMIT 1
                """, String.class)).isEqualTo("32");
    }

    private void insertSourceFixtures() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 25, 9, 30);
        source.update("""
                INSERT INTO sys_college
                    (id, code, name, sort, status, created_by, created_at,
                     updated_by, updated_at, deleted)
                VALUES (?, 'P41-COLLEGE', ?, 41, 1, 0, ?, 0, ?, 0)
                """, COLLEGE_ID, "Phase 41 恢复学院", now, now);
        source.update("""
                INSERT INTO sys_major
                    (id, college_id, internal_major_code, internal_major_name,
                     second_discipline_code, second_discipline_name, pilot_scope_flag,
                     year_version, sort, status, created_by, created_at,
                     updated_by, updated_at, deleted)
                VALUES (?, ?, 'P41-MAJOR', ?, '0401', '教育学', 0,
                        '2026', 41, 1, 0, ?, 0, ?, 0)
                """, MAJOR_ID, COLLEGE_ID, "Phase 41 恢复专业", now, now);
        source.update("""
                INSERT INTO notification
                    (id, user_id, type, title, content, biz_type, biz_id, read_flag,
                     created_by, created_at, updated_by, updated_at, deleted)
                VALUES (?, 0, 'P41_RESTORE', ?, ?, 'backup', 'P41', 0,
                        0, ?, 0, ?, 1)
                """, NOTIFICATION_ID, SPECIAL_TEXT, SPECIAL_TEXT, now, now);
        source.update("""
                INSERT INTO backup_record
                    (id, backup_type, status, scope, storage_uri, started_at, finished_at,
                     operator_id, remark, byte_size, checksum, table_count, row_count,
                     created_by, created_at, updated_by, updated_at, deleted)
                VALUES
                    (?, 'full', 'COMPLETED', 'P41-HISTORY',
                     'minio://teacher-cert/db-backup/p41-history.sql.gz', ?, ?,
                     0, '应保留的历史终态', 41, ?, 37, 410, 0, ?, 0, ?, 0),
                    (?, 'full', 'RUNNING', 'P41-CONCURRENT', NULL, ?, NULL,
                     0, '必须排除的并发运行任务', NULL, NULL, NULL, NULL, 0, ?, 0, ?, 0),
                    (?, 'full', 'PENDING', 'P41-PENDING', NULL, NULL, NULL,
                     0, '必须排除的待执行任务', NULL, NULL, NULL, NULL, 0, ?, 0, ?, 0)
                """,
                COMPLETED_BACKUP_ID, now.minusHours(2), now.minusHours(1),
                "sha256:4100000000000000000000000000000000000000000000000000000000000000",
                now.minusHours(2), now.minusHours(1),
                RUNNING_BACKUP_ID, now.minusMinutes(5), now.minusMinutes(5), now.minusMinutes(5),
                PENDING_BACKUP_ID, now, now);
        source.update("""
                INSERT INTO backup_record
                    (id, backup_type, status, scope, started_at, finished_at, operator_id,
                     remark, error_message, created_by, created_at, updated_by, updated_at, deleted)
                VALUES (?, 'full', 'FAILED', 'P41-HISTORY', ?, ?, 0,
                        '应保留的历史失败终态', '历史失败原因', 0, ?, 0, ?, 0)
                """, FAILED_BACKUP_ID, now.minusHours(4), now.minusHours(3),
                now.minusHours(4), now.minusHours(3));
    }

    private void insertRestoreOnlySentinel(JdbcTemplate restore) {
        restore.update("""
                INSERT INTO notification
                    (id, user_id, type, title, content, read_flag, deleted)
                VALUES (?, 0, 'P41_SENTINEL', '必须被精确恢复移除', 'target-only', 0, 0)
                """, RESTORE_SENTINEL_ID);
    }

    private String objectKey(BackupRecord backup) {
        String prefix = "minio://" + bucket + "/";
        assertThat(backup.getStorageUri()).startsWith(prefix);
        assertThat(backup.getByteSize()).isNotNull().isGreaterThan(0L);
        assertThat(backup.getChecksum()).startsWith("sha256:");
        return backup.getStorageUri().substring(prefix.length());
    }

    private static void replayArtifact(String jdbcUrl, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
             Statement statement = connection.createStatement()) {
            try {
                for (String physicalLine : sql.lines().toList()) {
                    String command = physicalLine.trim();
                    if (command.isEmpty() || command.startsWith("--")) {
                        continue;
                    }
                    if (command.endsWith(";")) {
                        command = command.substring(0, command.length() - 1);
                    }
                    statement.execute(command);
                }
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static Set<String> tableNames(JdbcTemplate jdbc) {
        return new TreeSet<>(jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, String.class));
    }

    private static Set<String> tableSections(String sql) {
        Set<String> sections = new TreeSet<>();
        Matcher matcher = TABLE_SECTION.matcher(sql);
        while (matcher.find()) {
            sections.add(matcher.group(1));
        }
        return sections;
    }

    private static Map<String, TableSnapshot> snapshots(
            JdbcTemplate jdbc,
            Set<String> tables) throws Exception {
        return snapshots(jdbc, tables, false);
    }

    private static Map<String, TableSnapshot> backupArtifactSnapshots(
            JdbcTemplate jdbc,
            Set<String> tables) throws Exception {
        return snapshots(jdbc, tables, true);
    }

    private static Map<String, TableSnapshot> snapshots(
            JdbcTemplate jdbc,
            Set<String> tables,
            boolean terminalBackupRecordsOnly) throws Exception {
        Map<String, TableSnapshot> result = new TreeMap<>();
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                for (String table : tables) {
                    result.put(table, snapshot(
                            connection,
                            table,
                            terminalBackupRecordsOnly && "backup_record".equals(table)));
                }
            } finally {
                connection.rollback();
            }
        }
        return result;
    }

    private static TableSnapshot snapshot(
            Connection connection,
            String table,
            boolean terminalBackupRecordsOnly) throws Exception {
        List<String> rowHashes = new ArrayList<>();
        MessageDigest tableDigest = MessageDigest.getInstance("SHA-256");
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT * FROM `" + table + "`"
                             + (terminalBackupRecordsOnly
                             ? " WHERE status IN ('COMPLETED','FAILED')"
                             : ""))) {
            ResultSetMetaData meta = resultSet.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                updateBytes(tableDigest, meta.getColumnName(i).getBytes(StandardCharsets.UTF_8));
                updateInt(tableDigest, meta.getColumnType(i));
            }
            while (resultSet.next()) {
                MessageDigest rowDigest = MessageDigest.getInstance("SHA-256");
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    byte[] value = resultSet.getBytes(i);
                    if (resultSet.wasNull()) {
                        updateInt(rowDigest, -1);
                    } else {
                        updateBytes(rowDigest, value);
                    }
                }
                rowHashes.add(HexFormat.of().formatHex(rowDigest.digest()));
            }
        }
        rowHashes.sort(String::compareTo);
        for (String rowHash : rowHashes) {
            updateBytes(tableDigest, rowHash.getBytes(StandardCharsets.US_ASCII));
        }
        return new TableSnapshot(rowHashes.size(), HexFormat.of().formatHex(tableDigest.digest()));
    }

    private static void updateBytes(MessageDigest digest, byte[] value) {
        updateInt(digest, value.length);
        digest.update(value);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static ScratchSchemas prepareScratchSchemas() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String sourceSchema = "teacher_cert_p41_source_" + suffix;
        String restoreSchema = "teacher_cert_p41_restore_" + suffix;
        String adminUrl = replaceDatabase(BASE_URL, "mysql");
        boolean sourceCreated = false;
        try (Connection admin = DriverManager.getConnection(adminUrl, USERNAME, PASSWORD);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + sourceSchema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            sourceCreated = true;
            statement.execute("CREATE DATABASE `" + restoreSchema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            return new ScratchSchemas(
                    sourceSchema,
                    restoreSchema,
                    replaceDatabase(BASE_URL, sourceSchema),
                    replaceDatabase(BASE_URL, restoreSchema));
        } catch (Exception e) {
            if (sourceCreated) {
                dropSchemaBestEffort(adminUrl, sourceSchema);
            }
            dropSchemaBestEffort(adminUrl, restoreSchema);
            throw new IllegalStateException("创建 Phase 41 scratch schema 失败", e);
        }
    }

    @AfterAll
    static void dropScratchSchemas() {
        String adminUrl = replaceDatabase(BASE_URL, "mysql");
        List<RuntimeException> failures = new ArrayList<>();
        try {
            dropSchema(adminUrl, SCRATCH.restoreSchema(), "teacher_cert_p41_restore_");
        } catch (RuntimeException e) {
            failures.add(e);
        }
        try {
            dropSchema(adminUrl, SCRATCH.sourceSchema(), "teacher_cert_p41_source_");
        } catch (RuntimeException e) {
            failures.add(e);
        }
        if (!failures.isEmpty()) {
            RuntimeException failure = failures.get(0);
            failures.stream().skip(1).forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static void dropSchema(String adminUrl, String schema, String requiredPrefix) {
        if (!schema.toLowerCase(Locale.ROOT).startsWith(requiredPrefix)) {
            throw new IllegalStateException("拒绝删除非 Phase 41 scratch schema: " + schema);
        }
        try (Connection admin = DriverManager.getConnection(adminUrl, USERNAME, PASSWORD);
             Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        } catch (Exception e) {
            throw new IllegalStateException("清理 Phase 41 scratch schema 失败: " + schema, e);
        }
    }

    private static void dropSchemaBestEffort(String adminUrl, String schema) {
        if (schema == null
                || (!schema.startsWith("teacher_cert_p41_source_")
                && !schema.startsWith("teacher_cert_p41_restore_"))) {
            return;
        }
        try (Connection admin = DriverManager.getConnection(adminUrl, USERNAME, PASSWORD);
             Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        } catch (Exception ignore) {
            // 类初始化失败路径只处理带唯一安全前缀的本测试 schema。
        }
    }

    private static String setting(String name, String defaultValue) {
        String systemValue = System.getProperty(name);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        String environmentValue = System.getenv(name);
        return environmentValue == null || environmentValue.isBlank()
                ? defaultValue : environmentValue;
    }

    private static String replaceDatabase(String jdbcUrl, String schema) {
        int queryIndex = jdbcUrl.indexOf('?');
        String withoutQuery = queryIndex < 0 ? jdbcUrl : jdbcUrl.substring(0, queryIndex);
        String query = queryIndex < 0 ? "" : jdbcUrl.substring(queryIndex);
        int slash = withoutQuery.lastIndexOf('/');
        if (slash < "jdbc:mysql://".length()) {
            throw new IllegalArgumentException("无法识别 MySQL JDBC URL");
        }
        return withoutQuery.substring(0, slash + 1) + schema + query;
    }

    private record ScratchSchemas(
            String sourceSchema,
            String restoreSchema,
            String sourceUrl,
            String restoreUrl) {
    }

    private record TableSnapshot(long rowCount, String sha256) {
    }
}
