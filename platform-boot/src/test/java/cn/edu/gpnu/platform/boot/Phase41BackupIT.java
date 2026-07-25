package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.BackupRecord;
import cn.edu.gpnu.platform.system.service.impl.DatabaseBackupService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

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
    private static final long STUDENT_ID = 341000000000000009L;
    private static final long CERTIFICATE_ID = 341000000000000010L;
    private static final long ACTIVE_FILE_ID = 341000000000000011L;
    private static final long READY_FILE_ID = 341000000000000012L;
    private static final long VIDEO_SESSION_ID = 341000000000000013L;
    private static final long MATERIAL_ID = 341000000000000014L;
    private static final long LARGE_BATCH_ID = 341000000000000015L;
    private static final long ADMIN_USER_ID = 800000000000003001L;
    private static final int LARGE_PREVIEW_PAYLOAD_BYTES = 13 * 1024 * 1024;
    private static final int MYSQL_DEFAULT_CLIENT_PACKET_BYTES = 16 * 1024 * 1024;
    private static final int DEFAULT_MAX_SQL_STATEMENT_BYTES = 32 * 1024 * 1024;
    private static final int REQUIRED_PACKET_BYTES = 64 * 1024 * 1024;
    private static final String ACTIVE_UPLOAD_CONTEXT_HASH =
            "4141414141414141414141414141414141414141414141414141414141414141";
    private static final String ACTIVE_UPLOAD_METADATA_HASH =
            "4242424242424242424242424242424242424242424242424242424242424242";
    private static final String SPECIAL_TEXT =
            "恢复快照：O'Neil 与反斜杠 \\\\ 保持原样\n第二行"
                    + '\0' + '\u001A' + " 😀";

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
        registry.add("platform.backup.bucket",
                () -> setting("MINIO_BUCKET", "teacher-cert"));
        registry.add("platform.backup.prefix",
                () -> setting("PLATFORM_BACKUP_PREFIX", "db-backup/"));
        registry.add("platform.security.jwt.secret",
                () -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        registry.add("platform.security.initial-password", () -> "Staff-Initial-2026!");
        registry.add("platform.backup.schedule.enabled", () -> false);
        registry.add("platform.backup.max-sql-statement-bytes",
                () -> DEFAULT_MAX_SQL_STATEMENT_BYTES);
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

    @Value("${platform.backup.prefix:db-backup/}")
    private String backupPrefix;

    @Test
    void fullArtifactReplacesFlywaySeedsAndRestoresExactSnapshotAtomically() throws Exception {
        insertSourceFixtures();
        Set<String> tables = tableNames(source);
        assertThat(tables).hasSize(37);
        assertGeneratedColumnValues(source);
        Map<String, TableSnapshot> expected = backupArtifactSnapshots(source, tables);
        long expectedRows = expected.values().stream().mapToLong(TableSnapshot::rowCount).sum();
        if (requiredMysqlCliRestore()) {
            assertMysqlCliGateStorageBoundary();
        }

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
                    .containsEntry("tcp-row-count", Long.toString(expectedRows))
                    .containsEntry("tcp-format-version", "TCP_SQL_V2")
                    .containsEntry("tcp-max-statement-bytes",
                            Integer.toString(DEFAULT_MAX_SQL_STATEMENT_BYTES))
                    .containsEntry("tcp-required-packet-bytes",
                            Integer.toString(REQUIRED_PACKET_BYTES));
            assertThat("sha256:" + sha256(gzipBytes)).isEqualTo(backup.getChecksum());

            String sql;
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gzipBytes))) {
                sql = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertArtifactContract(sql, backup, tables, expectedRows);
            assertGeneratedColumnsOmitted(sql, source);
            assertLargePreviewStatementBound(sql);

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
            assertGeneratedColumnValues(restore);
            assertLargePreviewRestored(restore);
            if (requiredMysqlCliRestore()) {
                replayArtifactWithMysql84Cli(restore, sql);
                assertRestoredSnapshot(
                        restore, tables, expected, backup.getId(), flywayRowsBefore);
                assertGeneratedColumnValues(restore);
                assertLargePreviewRestored(restore);
            }

            // 同一产物重复回放仍得到相同精确快照，不残留第一次恢复后的额外数据。
            insertRestoreOnlySentinel(restore);
            replayArtifact(SCRATCH.restoreUrl(), sql);
            assertRestoredSnapshot(restore, tables, expected, backup.getId(), flywayRowsBefore);
            assertGeneratedColumnValues(restore);
            assertLargePreviewRestored(restore);

            // 目标先制造 drift；故障放到唯一 COMMIT 前，确保全部正常 INSERT 已执行后仍整体回滚。
            insertRestoreOnlySentinel(restore);
            Map<String, TableSnapshot> beforeFailure = snapshots(restore, tables);
            assertThat(beforeFailure).isNotEqualTo(expected);
            assertThat(restore.queryForObject(
                    "SELECT COUNT(*) FROM notification WHERE id = ?",
                    Long.class, RESTORE_SENTINEL_ID)).isEqualTo(1L);
            String commitMarker = "\nCOMMIT;\n";
            assertThat(countOccurrences(sql, commitMarker)).isEqualTo(1);
            long artifactInsertCount = sql.lines()
                    .filter(line -> line.startsWith("INSERT INTO "))
                    .count();
            assertThat(artifactInsertCount).isEqualTo(expectedRows);
            int lastNormalInsert = sql.lastIndexOf("INSERT INTO ");
            assertThat(lastNormalInsert).isGreaterThanOrEqualTo(0);
            String broken = sql.replace(
                    commitMarker,
                    "\nINSERT INTO `phase41_missing_table` (`id`) VALUES (1);\nCOMMIT;\n");
            assertThat(countOccurrences(broken, commitMarker)).isEqualTo(1);
            int failureInsert = broken.indexOf("INSERT INTO `phase41_missing_table`");
            assertThat(failureInsert).isGreaterThan(lastNormalInsert);
            ReplayTrace trace = new ReplayTrace();
            Throwable failure = catchThrowable(
                    () -> replayArtifact(SCRATCH.restoreUrl(), broken, trace));
            assertThat(failure).isInstanceOf(SQLException.class);
            SQLException sqlFailure = (SQLException) failure;
            assertThat(sqlFailure.getSQLState()).isEqualTo("42S02");
            assertThat(sqlFailure.getMessage()).contains("phase41_missing_table");
            assertThat(trace.failedCommand())
                    .isEqualTo("INSERT INTO `phase41_missing_table` (`id`) VALUES (1)");
            assertThat(trace.successfulInserts())
                    .isEqualTo(artifactInsertCount)
                    .isEqualTo(expectedRows);
            assertThat(trace.commitExecuted()).isFalse();
            assertThat(trace.rollbackSucceeded()).isTrue();
            assertThat(snapshots(restore, tables)).isEqualTo(beforeFailure);
            assertThat(snapshots(restore, tables)).isNotEqualTo(expected);
            assertThat(restore.queryForObject(
                    "SELECT COUNT(*) FROM notification WHERE id = ?",
                    Long.class, RESTORE_SENTINEL_ID)).isEqualTo(1L);
        } finally {
            // 仅清理由本测试精确创建的对象；失败必须使测试失败，避免绿灯遗留测试资产。
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
        }
        assertOversizedStatementFailsClosed();
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
        assertThat(sql).contains(
                "-- formatVersion=TCP_SQL_V2 generatedColumns=OMITTED textEncoding=BASE64"
                        + " maxStatementBytes=" + DEFAULT_MAX_SQL_STATEMENT_BYTES
                        + " requiredPacketBytes=" + REQUIRED_PACKET_BYTES);
        assertThat(sql).contains("-- backupType=full scope=P41  RESTORE-DRILL recordId=");
        assertThat(sql).doesNotContain("\nRESTORE-DRILL");
        assertThat(sql).contains("START TRANSACTION;");
        assertThat(sql).contains("DELETE FROM `sys_permission`;");
        assertThat(sql).contains("INSERT INTO `sys_permission`");
        assertThat(sql).contains("FROM_BASE64('");
        assertThat(sql).doesNotContain("CONVERT(0x");
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
                INSERT INTO student
                    (id, student_no, name, gender, id_card_type, id_card_no,
                     birth_date, identity_type, source_province, source_city,
                     source_county, source_full, college_id, grade, class_name,
                     status, locked, created_by, created_at, updated_by, updated_at, deleted)
                VALUES
                    (?, 'P41S0001', 'Phase 41 生成列学生', 'female',
                     'resident_id_card', '11010119900628002X', '1990/6/28',
                     'normal_student', '440000', '440100', '440106',
                     '广东省/广州市/天河区', ?, '2022', 'Phase41班',
                     'PASSED', 1, 0, ?, 0, ?, 0)
                """, STUDENT_ID, COLLEGE_ID, now, now);
        source.update("""
                INSERT INTO certificate
                    (id, student_id, college_id, assessment_year, cert_no,
                     student_no, student_name, id_card_type, id_card_no,
                     education_level, training_goal, teaching_segment,
                     teaching_subject_code, teaching_subject_name, issue_date,
                     valid_until, status, locked, created_by, created_at,
                     updated_by, updated_at, deleted)
                VALUES
                    (?, ?, ?, '2026', '202610588344400001', 'P41S0001',
                     'Phase 41 生成列学生', 'resident_id_card',
                     '11010119900628002X', 'bachelor', '中学教师培养目标',
                     'senior_middle_school', 'sms_math', '数学', '2026/6/30',
                     '2029/6/30', 'GENERATED', 1, 0, ?, 0, ?, 0)
                """, CERTIFICATE_ID, STUDENT_ID, COLLEGE_ID, now, now);
        source.update("""
                INSERT INTO file_object
                    (id, original_name, stored_name, bucket, object_key, `size`,
                     content_type, md5, biz_type, status, multipart_upload_id,
                     upload_part_size, upload_total_parts, upload_expires_at,
                     upload_context_hash, upload_metadata_hash, uploader_id,
                     upload_time, created_by, created_at, updated_by, updated_at, deleted)
                VALUES
                    (?, 'p41-active.pdf', 'p41-active.pdf', 'teacher-cert',
                     'process-material/p41-active.pdf', 5242880, 'application/pdf',
                     'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'process-material',
                     'INITIATING', NULL, 5242880, 1, NULL, ?, ?, ?, ?, 0, ?, 0, ?, 0),
                    (?, 'p41-ready.pdf', 'p41-ready.pdf', 'teacher-cert',
                     'process-material/p41-ready.pdf', 41, 'application/pdf',
                     'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'process-material',
                     'READY', NULL, NULL, NULL, NULL, NULL, NULL, ?, ?, 0, ?, 0, ?, 0)
                """,
                ACTIVE_FILE_ID, ACTIVE_UPLOAD_CONTEXT_HASH, ACTIVE_UPLOAD_METADATA_HASH,
                ADMIN_USER_ID, now, now, now,
                READY_FILE_ID, ADMIN_USER_ID, now, now, now);
        source.update("""
                INSERT INTO video_upload_session
                    (id, upload_id, upload_mode, student_id, college_id,
                     assessment_year, file_md5, file_name, file_size, content_type,
                     chunk_size, total_chunks, uploaded_chunks, uploaded_bytes,
                     duration_seconds, status, slot_claimed, created_by, created_at,
                     updated_by, updated_at, deleted)
                VALUES
                    (?, 'p41-generated-column-session', 'SERVER_CHUNK', ?, ?,
                     '2026', 'cccccccccccccccccccccccccccccccc',
                     'p41-video.mp4', 1048576, 'video/mp4', 1048576, 1, 0, 0,
                     900, 'UPLOADING', 1, 0, ?, 0, ?, 0)
                """, VIDEO_SESSION_ID, STUDENT_ID, COLLEGE_ID, now, now);
        source.update("""
                INSERT INTO process_material
                    (id, student_id, college_id, assessment_year, category,
                     file_id, file_name, file_path, file_size, content_type,
                     uploader_id, upload_time, status, locked, created_by,
                     created_at, updated_by, updated_at, deleted)
                VALUES
                    (?, ?, ?, '2026', 'morality_teacher_ethics', ?,
                     'p41-ready.pdf', 'process-material/p41-ready.pdf', 41,
                     'application/pdf', ?, ?, 'DRAFT', 0, 0, ?, 0, ?, 0)
                """, MATERIAL_ID, STUDENT_ID, COLLEGE_ID, READY_FILE_ID,
                ADMIN_USER_ID, now, now, now);
        source.update("""
                INSERT INTO import_export_batch
                    (id, batch_no, type, file_name, operator_id, operate_time,
                     total, success_count, fail_count, preview_json, strategy,
                     status, remark, created_by, created_at, updated_by, updated_at, deleted)
                VALUES
                    (?, 'P41-LARGE-PREVIEW', 'import', 'p41-large-preview.xlsx',
                     0, ?, 1, 1, 0,
                     JSON_OBJECT('payload', REPEAT('x', ?)),
                     'skip', 'PREVALIDATED', '验证大 JSON 恢复包边界',
                     0, ?, 0, ?, 0)
                """, LARGE_BATCH_ID, now, LARGE_PREVIEW_PAYLOAD_BYTES, now, now);
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

    private void assertGeneratedColumnsOmitted(String sql, JdbcTemplate jdbc) {
        List<GeneratedColumn> generatedColumns = jdbc.query("""
                SELECT table_name, column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND generation_expression IS NOT NULL
                  AND generation_expression <> ''
                ORDER BY table_name, ordinal_position
                """, (rs, rowNum) -> new GeneratedColumn(
                rs.getString("table_name"),
                rs.getString("column_name")));
        assertThat(generatedColumns).hasSize(5);
        for (GeneratedColumn generated : generatedColumns) {
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + generated.table() + "`",
                    Long.class)).isGreaterThan(0L);
            Pattern insertPattern = Pattern.compile(
                    "(?m)^INSERT INTO `" + Pattern.quote(generated.table())
                            + "` \\(([^\\r\\n]+)\\) VALUES ");
            Matcher matcher = insertPattern.matcher(sql);
            assertThat(matcher.find())
                    .as("含生成列的非空表必须出现在产物中: %s", generated.table())
                    .isTrue();
            do {
                assertThat(matcher.group(1))
                        .doesNotContain("`" + generated.column() + "`");
            } while (matcher.find());
        }
    }

    private void assertGeneratedColumnValues(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject(
                "SELECT idcard_key FROM student WHERE id = ?",
                String.class, STUDENT_ID)).isEqualTo("11010119900628002X");
        assertThat(jdbc.queryForObject(
                "SELECT active_key FROM certificate WHERE id = ?",
                String.class, CERTIFICATE_ID)).isEqualTo(STUDENT_ID + "-2026");
        assertThat(jdbc.queryForObject(
                "SELECT active_upload_key FROM file_object WHERE id = ?",
                String.class, ACTIVE_FILE_ID))
                .isEqualTo(ADMIN_USER_ID + "-process-material-" + ACTIVE_UPLOAD_CONTEXT_HASH);
        assertThat(jdbc.queryForObject(
                "SELECT active_slot_key FROM video_upload_session WHERE id = ?",
                String.class, VIDEO_SESSION_ID)).isEqualTo(STUDENT_ID + "-2026");
        assertThat(jdbc.queryForObject(
                "SELECT active_file_key FROM process_material WHERE id = ?",
                Long.class, MATERIAL_ID)).isEqualTo(READY_FILE_ID);
    }

    private void assertLargePreviewStatementBound(String sql) {
        String batchStatement = sql.lines()
                .filter(line -> line.startsWith("INSERT INTO `import_export_batch`"))
                .filter(line -> line.contains("P41-LARGE-PREVIEW")
                        || line.contains("FROM_BASE64"))
                .max((left, right) -> Integer.compare(
                        left.getBytes(StandardCharsets.UTF_8).length,
                        right.getBytes(StandardCharsets.UTF_8).length))
                .orElseThrow();
        int statementBytes = batchStatement.getBytes(StandardCharsets.UTF_8).length;
        assertThat(statementBytes)
                .isGreaterThan(MYSQL_DEFAULT_CLIENT_PACKET_BYTES)
                .isLessThanOrEqualTo(DEFAULT_MAX_SQL_STATEMENT_BYTES);
        assertThat(sql.lines()
                .mapToInt(line -> line.getBytes(StandardCharsets.UTF_8).length)
                .max()
                .orElseThrow()).isLessThanOrEqualTo(DEFAULT_MAX_SQL_STATEMENT_BYTES);
    }

    private void assertLargePreviewRestored(JdbcTemplate restore) {
        Integer restoredBytes = restore.queryForObject("""
                SELECT LENGTH(JSON_UNQUOTE(JSON_EXTRACT(preview_json, '$.payload')))
                FROM import_export_batch
                WHERE id = ?
                """, Integer.class, LARGE_BATCH_ID);
        String sourceHash = source.queryForObject("""
                SELECT SHA2(JSON_UNQUOTE(JSON_EXTRACT(preview_json, '$.payload')), 256)
                FROM import_export_batch
                WHERE id = ?
                """, String.class, LARGE_BATCH_ID);
        String restoredHash = restore.queryForObject("""
                SELECT SHA2(JSON_UNQUOTE(JSON_EXTRACT(preview_json, '$.payload')), 256)
                FROM import_export_batch
                WHERE id = ?
                """, String.class, LARGE_BATCH_ID);
        assertThat(restoredBytes).isEqualTo(LARGE_PREVIEW_PAYLOAD_BYTES);
        assertThat(restoredHash).isEqualTo(sourceHash);
    }

    private void assertOversizedStatementFailsClosed() {
        ReflectionTestUtils.setField(
                backupService, "maxSqlStatementBytes", 1024 * 1024);
        try {
            assertThatThrownBy(() -> backupService.backup(
                    "full", "P41-STATEMENT-LIMIT", "单语句超限必须失败关闭", 0L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("备份行 SQL 超出单语句上限")
                    .hasMessageContaining("table=import_export_batch")
                    .hasMessageNotContaining("xxxxxxxx");
            Map<String, Object> failed = source.queryForMap("""
                    SELECT status, storage_uri, checksum, error_message
                    FROM backup_record
                    WHERE scope = 'P41-STATEMENT-LIMIT'
                    ORDER BY id DESC
                    LIMIT 1
                    """);
            assertThat(failed)
                    .containsEntry("status", "FAILED")
                    .containsEntry("storage_uri", null)
                    .containsEntry("checksum", null);
            assertThat((String) failed.get("error_message"))
                    .contains("table=import_export_batch")
                    .contains("maxBytes=1048576")
                    .doesNotContain("xxxxxxxx");
        } finally {
            ReflectionTestUtils.setField(
                    backupService, "maxSqlStatementBytes", DEFAULT_MAX_SQL_STATEMENT_BYTES);
        }
    }

    private void assertMysqlCliGateStorageBoundary() throws Exception {
        assertThat(bucket)
                .as("真实 CLI 门禁的最终 bucket 必须等于写前预检目标")
                .isEqualTo(setting("MINIO_BUCKET", ""));
        assertThat(backupPrefix)
                .as("真实 CLI 门禁的最终对象前缀必须等于写前预检目标")
                .isEqualTo(setting("PLATFORM_BACKUP_PREFIX", ""));
        assertThat(bucket)
                .as("真实 CLI 门禁只允许使用预先创建的 Phase 41 专用测试桶")
                .matches("teacher-cert-p41-[a-z0-9][a-z0-9-]{2,50}");
        assertThat(minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build()))
                .as("真实 CLI 门禁不会创建 bucket，专用测试桶必须预先存在")
                .isTrue();
        assertThat(backupPrefix)
                .as("真实 CLI 门禁必须使用本次运行独占的对象前缀")
                .matches("db-backup/phase41-cli/[A-Za-z0-9_-]{8,64}/?");
    }

    /**
     * 用户控制的真实 CLI 门禁。默认关闭；开启后只接受 Linux + MySQL 8.4 客户端，
     * 并用 0600 option file 连接本测试创建的唯一 restore scratch schema。
     */
    private void replayArtifactWithMysql84Cli(JdbcTemplate restore, String sql) throws Exception {
        assertThat(System.getProperty("os.name").toLowerCase(Locale.ROOT))
                .as("真实 mysql CLI 门禁只允许在获授权的 Linux/WSL 隔离环境执行")
                .contains("linux");
        assertThat(restore.queryForObject("SELECT VERSION()", String.class))
                .startsWith("8.4.");
        assertThat(restore.queryForObject(
                "SELECT @@GLOBAL.max_allowed_packet", Long.class))
                .isGreaterThanOrEqualTo(REQUIRED_PACKET_BYTES);

        String executable = setting("PHASE41_MYSQL_CLI", "mysql");
        URI uri = URI.create(SCRATCH.restoreUrl().substring("jdbc:".length()));
        int port = uri.getPort() < 0 ? 3306 : uri.getPort();
        Path optionFile = Files.createTempFile("tcp-p41-mysql-", ".cnf");
        Path sqlFile = Files.createTempFile("tcp-p41-artifact-", ".sql");
        try {
            Files.setPosixFilePermissions(
                    optionFile, PosixFilePermissions.fromString("rw-------"));
            Files.setPosixFilePermissions(
                    sqlFile, PosixFilePermissions.fromString("rw-------"));
            Files.writeString(optionFile, """
                    [client]
                    user="%s"
                    password="%s"
                    host="%s"
                    port=%d
                    protocol=tcp
                    """.formatted(
                    optionValue(USERNAME),
                    optionValue(PASSWORD),
                    optionValue(uri.getHost()),
                    port), StandardCharsets.UTF_8);
            Files.writeString(sqlFile, sql, StandardCharsets.UTF_8);

            ProcessBuilder versionBuilder = new ProcessBuilder(
                    executable,
                    "--no-login-paths",
                    "--defaults-file=" + optionFile,
                    "--version")
                    .redirectErrorStream(true);
            isolateMysqlEnvironment(versionBuilder);
            Process version = versionBuilder.start();
            byte[] versionOutput = version.getInputStream().readAllBytes();
            assertThat(version.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(version.exitValue()).isZero();
            assertThat(new String(versionOutput, StandardCharsets.UTF_8))
                    .containsPattern("(?i)Ver\\s+8\\.4\\.");

            ProcessBuilder restoreBuilder = new ProcessBuilder(
                    executable,
                    "--no-login-paths",
                    "--defaults-file=" + optionFile,
                    "--binary-mode",
                    "--default-character-set=utf8mb4",
                    "--max-allowed-packet=" + REQUIRED_PACKET_BYTES,
                    SCRATCH.restoreSchema())
                    .redirectInput(sqlFile.toFile())
                    .redirectErrorStream(true);
            isolateMysqlEnvironment(restoreBuilder);
            Process process = restoreBuilder.start();
            boolean finished = process.waitFor(180, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("mysql 8.4 CLI 恢复在 180 秒内未自然退出");
            }
            String output = new String(
                    process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue())
                    .as("mysql 8.4 CLI 恢复失败，输出=%s", output)
                    .isZero();
        } finally {
            Files.deleteIfExists(sqlFile);
            Files.deleteIfExists(optionFile);
        }
    }

    private static boolean requiredMysqlCliRestore() {
        String environmentValue = System.getenv("PHASE41_REQUIRE_MYSQL_CLI_RESTORE");
        String systemValue = System.getProperty("PHASE41_REQUIRE_MYSQL_CLI_RESTORE");
        if ("1".equals(systemValue) && !"1".equals(environmentValue)) {
            throw new IllegalStateException(
                    "真实 CLI 门禁只能由用户脚本通过环境变量开启，拒绝仅用 JVM -D 绕过");
        }
        return "1".equals(setting("PHASE41_REQUIRE_MYSQL_CLI_RESTORE", "0"));
    }

    private static void isolateMysqlEnvironment(ProcessBuilder builder) {
        Map<String, String> environment = builder.environment();
        environment.remove("MYSQL_PWD");
        environment.remove("MYSQL_HOST");
        environment.remove("MYSQL_TCP_PORT");
        environment.remove("MYSQL_UNIX_PORT");
        environment.remove("MYSQL_TEST_LOGIN_FILE");
        environment.remove("MYSQL_GROUP_SUFFIX");
    }

    private static String optionValue(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String objectKey(BackupRecord backup) {
        String prefix = "minio://" + bucket + "/";
        assertThat(backup.getStorageUri()).startsWith(prefix);
        assertThat(backup.getByteSize()).isNotNull().isGreaterThan(0L);
        assertThat(backup.getChecksum()).startsWith("sha256:");
        return backup.getStorageUri().substring(prefix.length());
    }

    private static void replayArtifact(String jdbcUrl, String sql) throws Exception {
        replayArtifact(jdbcUrl, sql, new ReplayTrace());
    }

    private static void replayArtifact(
            String jdbcUrl,
            String sql,
            ReplayTrace trace) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
             Statement statement = connection.createStatement()) {
            String currentCommand = null;
            try {
                for (String physicalLine : sql.lines().toList()) {
                    String command = physicalLine.trim();
                    if (command.isEmpty() || command.startsWith("--")) {
                        continue;
                    }
                    if (command.endsWith(";")) {
                        command = command.substring(0, command.length() - 1);
                    }
                    currentCommand = command;
                    statement.execute(command);
                    if (command.startsWith("INSERT INTO ")) {
                        trace.recordSuccessfulInsert();
                    }
                    if ("COMMIT".equals(command)) {
                        trace.recordCommitExecuted();
                    }
                }
            } catch (Exception e) {
                trace.recordFailedCommand(currentCommand);
                try {
                    statement.execute("ROLLBACK");
                    trace.recordRollbackSucceeded();
                } catch (Exception rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            }
        }
    }

    private static int countOccurrences(String value, String target) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
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
        assertCliGateTargetsBeforeMutation();
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

    /**
     * 真实 CLI 门禁在创建 scratch schema 之前先只读确认目标：仅本机回环依赖、
     * 预先存在的专用 bucket 与本次运行独占前缀。这样 Spring 的 bucket initializer
     * 没有机会把“缺失专用桶”悄悄变成新建资源。
     */
    private static void assertCliGateTargetsBeforeMutation() {
        if (!requiredMysqlCliRestore()) {
            return;
        }
        URI jdbcUri = URI.create(BASE_URL.substring("jdbc:".length()));
        assertLoopbackEndpoint(jdbcUri, "MySQL");

        String endpoint = setting("MINIO_ENDPOINT", "");
        String accessKey = setting("MINIO_ACCESS_KEY", "");
        String secretKey = setting("MINIO_SECRET_KEY", "");
        String targetBucket = setting("MINIO_BUCKET", "");
        String targetPrefix = setting("PLATFORM_BACKUP_PREFIX", "");
        URI minioUri = URI.create(endpoint);
        assertLoopbackEndpoint(minioUri, "MinIO");
        if (!targetBucket.matches("teacher-cert-p41-[a-z0-9][a-z0-9-]{2,50}")) {
            throw new IllegalStateException(
                    "真实 CLI 门禁只允许预先存在的 teacher-cert-p41-* 专用测试桶");
        }
        if (!targetPrefix.matches("db-backup/phase41-cli/[A-Za-z0-9_-]{8,64}/?")) {
            throw new IllegalStateException("真实 CLI 门禁缺少本次运行独占对象前缀");
        }
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("真实 CLI 门禁必须显式提供专用 MinIO 凭据");
        }
        try {
            MinioClient preflight = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            if (!preflight.bucketExists(
                    BucketExistsArgs.builder().bucket(targetBucket).build())) {
                throw new IllegalStateException(
                        "Phase 41 专用测试桶必须在门禁前由用户预先创建");
            }
            var existingObjects = preflight.listObjects(ListObjectsArgs.builder()
                    .bucket(targetBucket)
                    .prefix(targetPrefix)
                    .recursive(true)
                    .build()).iterator();
            if (existingObjects.hasNext()) {
                existingObjects.next().get();
                throw new IllegalStateException(
                        "真实 CLI 门禁的本次对象前缀必须在运行前为空");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("只读核验 Phase 41 专用测试桶失败", e);
        }
    }

    private static void assertLoopbackEndpoint(URI uri, String target) {
        String scheme = uri.getScheme();
        boolean expectedScheme = "MySQL".equals(target)
                ? "mysql".equalsIgnoreCase(scheme)
                : "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        String host = uri.getHost();
        int port = uri.getPort();
        String authority = uri.getRawAuthority();
        boolean exactAuthority = authority != null
                && host != null
                && authority.equalsIgnoreCase(host + ":" + port);
        if (!expectedScheme
                || uri.getRawUserInfo() != null
                || (!"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host))
                || port < 1
                || port > 65535
                || !exactAuthority) {
            throw new IllegalStateException(
                    "真实 CLI 门禁的 " + target
                            + " 必须是单一 localhost/127.0.0.1:port endpoint");
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
        String environmentValue = System.getenv(name);
        if (cliGateRequestedByEnvironment() && isCliGateEnvironmentSetting(name)) {
            if (systemValue != null
                    && !systemValue.isBlank()
                    && !systemValue.equals(environmentValue)) {
                throw new IllegalStateException(
                        "真实 CLI 门禁拒绝 JVM system property 覆盖用户已核验的环境变量: "
                                + name);
            }
            return environmentValue == null || environmentValue.isBlank()
                    ? defaultValue : environmentValue;
        }
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        return environmentValue == null || environmentValue.isBlank()
                ? defaultValue : environmentValue;
    }

    private static boolean cliGateRequestedByEnvironment() {
        return "1".equals(System.getenv("PHASE41_REQUIRE_MYSQL_CLI_RESTORE"));
    }

    private static boolean isCliGateEnvironmentSetting(String name) {
        return switch (name) {
            case "PHASE41_REQUIRE_MYSQL_CLI_RESTORE",
                    "PHASE41_MYSQL_CLI",
                    "SPRING_DATASOURCE_URL",
                    "SPRING_DATASOURCE_USERNAME",
                    "SPRING_DATASOURCE_PASSWORD",
                    "SPRING_DATA_REDIS_HOST",
                    "SPRING_DATA_REDIS_PORT",
                    "SPRING_DATA_REDIS_PASSWORD",
                    "MINIO_ENDPOINT",
                    "MINIO_ACCESS_KEY",
                    "MINIO_SECRET_KEY",
                    "MINIO_BUCKET",
                    "PLATFORM_BACKUP_PREFIX" -> true;
            default -> false;
        };
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

    private record GeneratedColumn(String table, String column) {
    }

    private static final class ReplayTrace {
        private long successfulInserts;
        private String failedCommand;
        private boolean commitExecuted;
        private boolean rollbackSucceeded;

        void recordSuccessfulInsert() {
            successfulInserts++;
        }

        void recordFailedCommand(String command) {
            failedCommand = command;
        }

        void recordCommitExecuted() {
            commitExecuted = true;
        }

        void recordRollbackSucceeded() {
            rollbackSucceeded = true;
        }

        long successfulInserts() {
            return successfulInserts;
        }

        String failedCommand() {
            return failedCommand;
        }

        boolean commitExecuted() {
            return commitExecuted;
        }

        boolean rollbackSucceeded() {
            return rollbackSucceeded;
        }
    }
}
