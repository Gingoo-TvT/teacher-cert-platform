package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.video.support.VideoFinalizationObjectReconciler;
import cn.edu.gpnu.platform.business.video.support.VideoFinalizationObjectStore;
import cn.edu.gpnu.platform.system.entity.BackupRecord;
import cn.edu.gpnu.platform.system.service.impl.DatabaseBackupService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WS-3 第六轮：V32 候选账本必须进入应用全量备份，并能在隔离 scratch schema 中逐字段恢复后继续真实对账。
 *
 * <p>本用例只回放 candidate section：Phase 41 整库普通 INSERT 与 Flyway 种子冲突仍由其独立退回项跟踪，
 * 不在本工作包内虚报为已闭环。对象存储删除使用内存替身，恢复验证不会触碰真实视频对象。
 */
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = {
                PlatformApplication.class,
                Ws03CandidateBackupRestoreIT.SafeObjectStoreConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Ws03CandidateBackupRestoreIT {

    private static final Pattern TABLE_SECTION =
            Pattern.compile("(?m)^-- ---------- ([a-z0-9_]+) ----------\\r?$");
    private static final String SOURCE_URL = setting(
            "SPRING_DATASOURCE_URL",
            "jdbc:mysql://localhost:3306/teacher_cert"
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true&useSSL=false");
    private static final String USERNAME =
            setting("SPRING_DATASOURCE_USERNAME", "root");
    private static final String PASSWORD =
            setting("SPRING_DATASOURCE_PASSWORD", "root123");
    private static final String SCRATCH_SCHEMA =
            "teacher_cert_ws3_restore_" + UUID.randomUUID().toString().replace("-", "");
    private static final String SCRATCH_URL = prepareScratchSchema();

    @DynamicPropertySource
    static void scratchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> SCRATCH_URL);
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
    private VideoFinalizationObjectReconciler reconciler;

    @Autowired
    private RecordingObjectStore objectStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MinioClient minioClient;

    @Value("${platform.backup.bucket:${minio.bucket}}")
    private String bucket;

    @Test
    void candidateLedgerRoundTripsAllFieldsAndReconciliationContinues() throws Exception {
        String uploadId = "ws3-restore-" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime due = now.minusHours(2);
        LocalDateTime retired = now.minusHours(3);
        LocalDateTime created = now.minusDays(1);

        insertCandidate(
                331000000000000001L, uploadId, 41L, "candidate/g-41.mp4",
                "CLEANUP_PENDING", retired, due, due, 2,
                null, null, due, "previous remove timeout", null, created, now);
        insertCandidate(
                331000000000000002L, uploadId, 42L, "candidate/g-42.mp4",
                "CLEANING", retired, due, due, 3,
                "expired-claim-owner", due, due, "worker interrupted", null, created, now);
        insertCandidate(
                331000000000000003L, uploadId, 43L, "candidate/g-43.mp4",
                "CLEANED", retired, due, due, 4,
                null, null, due, null, due, created, now);

        List<Map<String, Object>> expectedRows = candidateRows(uploadId);
        BackupRecord backup = backupService.backup(
                "full", "WS3-CANDIDATE-RESTORE", "V32候选账本恢复反例", 0L);
        String objectKey = objectKey(backup);
        try {
            String sql = downloadSql(objectKey);
            Set<String> schemaTables = new TreeSet<>(jdbcTemplate.queryForList("""
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_type = 'BASE TABLE'
                      AND table_name <> 'flyway_schema_history'
                    """, String.class));
            Set<String> backupSections = tableSections(sql);
            assertThat(backup.getTableCount()).isEqualTo(37);
            assertThat(backupSections).containsExactlyInAnyOrderElementsOf(schemaTables);
            assertThat(backupSections).contains("video_finalization_object_candidate");

            List<String> candidateInserts = sql.lines()
                    .filter(line -> line.startsWith(
                            "INSERT INTO `video_finalization_object_candidate`"))
                    .toList();
            assertThat(candidateInserts).hasSize(3);

            jdbcTemplate.update(
                    "DELETE FROM video_finalization_object_candidate WHERE upload_id = ?",
                    uploadId);
            candidateInserts.forEach(jdbcTemplate::execute);

            List<Map<String, Object>> restoredRows = candidateRows(uploadId);
            assertThat(restoredRows)
                    .usingRecursiveComparison()
                    .isEqualTo(expectedRows);

            VideoFinalizationObjectReconciler.ReconcileResult result =
                    reconciler.reconcileDue();
            assertThat(result.scanned()).isEqualTo(3);
            assertThat(result.cleaned()).isEqualTo(3);
            assertThat(result.failed()).isZero();
            assertThat(objectStore.removedKeys()).containsExactlyInAnyOrder(
                    bucket + "/candidate/g-41.mp4",
                    bucket + "/candidate/g-42.mp4",
                    bucket + "/candidate/g-43.mp4");
            assertThat(jdbcTemplate.queryForList("""
                    SELECT state
                    FROM video_finalization_object_candidate
                    WHERE upload_id = ?
                    ORDER BY finalization_generation
                    """, String.class, uploadId))
                    .containsExactly("CLEANED", "CLEANED", "CLEANED");
            assertThat(jdbcTemplate.queryForList("""
                    SELECT attempt_count
                    FROM video_finalization_object_candidate
                    WHERE upload_id = ?
                    ORDER BY finalization_generation
                    """, Integer.class, uploadId))
                    .containsExactly(3, 4, 5);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM video_finalization_object_candidate
                    WHERE upload_id = ?
                      AND claim_owner IS NULL
                      AND claim_expires_at IS NULL
                      AND cleaned_at IS NOT NULL
                    """, Long.class, uploadId)).isEqualTo(3L);
        } finally {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket).object(objectKey).build());
            } catch (Exception ignore) {
                // 隔离测试产物 best-effort 清理；scratch schema 会在类结束时强制删除。
            }
        }
    }

    private void insertCandidate(
            long id,
            String uploadId,
            long generation,
            String objectKey,
            String state,
            LocalDateTime retiredAt,
            LocalDateTime cleanupNotBefore,
            LocalDateTime nextRetryAt,
            int attemptCount,
            String claimOwner,
            LocalDateTime claimExpiresAt,
            LocalDateTime lastAttemptAt,
            String lastError,
            LocalDateTime cleanedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        jdbcTemplate.update("""
                INSERT INTO video_finalization_object_candidate
                    (id, upload_id, finalization_generation, upload_mode, bucket, object_key,
                     state, retired_at, cleanup_not_before, next_retry_at, attempt_count,
                     claim_owner, claim_expires_at, last_attempt_at, last_error,
                     registered_file_id, cleaned_at,
                     created_by, created_at, updated_by, updated_at, deleted)
                VALUES
                    (?, ?, ?, 'SERVER_CHUNK', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                     NULL, ?, 0, ?, 0, ?, 0)
                """,
                id, uploadId, generation, bucket, objectKey, state,
                retiredAt, cleanupNotBefore, nextRetryAt, attemptCount,
                claimOwner, claimExpiresAt, lastAttemptAt, lastError,
                cleanedAt, createdAt, updatedAt);
    }

    private List<Map<String, Object>> candidateRows(String uploadId) {
        return jdbcTemplate.queryForList("""
                SELECT *
                FROM video_finalization_object_candidate
                WHERE upload_id = ?
                ORDER BY finalization_generation
                """, uploadId);
    }

    private String downloadSql(String objectKey) throws Exception {
        try (InputStream raw = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(objectKey).build());
             GZIPInputStream gzip = new GZIPInputStream(raw)) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String objectKey(BackupRecord backup) {
        String prefix = "minio://" + bucket + "/";
        assertThat(backup.getStatus()).isEqualTo("COMPLETED");
        assertThat(backup.getStorageUri()).startsWith(prefix);
        return backup.getStorageUri().substring(prefix.length());
    }

    private Set<String> tableSections(String sql) {
        Set<String> tables = new TreeSet<>();
        Matcher matcher = TABLE_SECTION.matcher(sql);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    private static String prepareScratchSchema() {
        if (!SCRATCH_SCHEMA.toLowerCase(Locale.ROOT)
                .startsWith("teacher_cert_ws3_restore_")) {
            throw new IllegalStateException("scratch schema 前缀不安全");
        }
        try (Connection admin = DriverManager.getConnection(SOURCE_URL, USERNAME, PASSWORD);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + SCRATCH_SCHEMA
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            return replaceDatabase(SOURCE_URL, SCRATCH_SCHEMA);
        } catch (Exception e) {
            throw new IllegalStateException("创建 WS-3 恢复 scratch schema 失败", e);
        }
    }

    @AfterAll
    static void dropScratchSchema() throws Exception {
        if (!SCRATCH_SCHEMA.toLowerCase(Locale.ROOT)
                .startsWith("teacher_cert_ws3_restore_")) {
            throw new IllegalStateException("拒绝删除非 WS-3 scratch schema");
        }
        try (Connection admin = DriverManager.getConnection(SOURCE_URL, USERNAME, PASSWORD);
             Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + SCRATCH_SCHEMA + "`");
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
            throw new IllegalArgumentException("无法识别MySQL JDBC URL");
        }
        return withoutQuery.substring(0, slash + 1) + schema + query;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SafeObjectStoreConfiguration {

        @Bean
        @Primary
        RecordingObjectStore recordingObjectStore() {
            return new RecordingObjectStore();
        }
    }

    static final class RecordingObjectStore implements VideoFinalizationObjectStore {

        private final List<String> removedKeys = new CopyOnWriteArrayList<>();

        @Override
        public void remove(String bucket, String objectKey) {
            removedKeys.add(bucket + "/" + objectKey);
        }

        @Override
        public boolean exists(String bucket, String objectKey) {
            return false;
        }

        List<String> removedKeys() {
            return new ArrayList<>(removedKeys);
        }
    }
}
