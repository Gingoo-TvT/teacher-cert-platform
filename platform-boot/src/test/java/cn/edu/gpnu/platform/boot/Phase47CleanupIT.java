package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.system.service.impl.RetentionCleanupService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 47（P1-9 定时清理）：直接调用保留期清理服务，证明
 * 「过期行被物理删除、近期行保留、运行中备份保留、其它业务表不受影响」。
 *
 * <p>不依赖调度器触发（{@code CleanupScheduleConfig} 默认门禁关闭、测试环境不注册）——直接
 * autowire {@link RetentionCleanupService} 调其方法。真实 MySQL（dev 栈），用独特标记行 +
 * 高位 id 段，finally 自清理，不污染验收数据。断言用 {@code >=1} 容忍并发/存量真实过期行，
 * 但通过唯一标记行精确校验「过期删/近期留」，并核验业务表行数不变。
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
})
class Phase47CleanupIT {

    private static final String MARK = "P47_IT";
    // 独特高位 id 段，避免与真实数据/并发碰撞
    private static final long ID_AUDIT_OLD = 9_047_000_001L;
    private static final long ID_AUDIT_NEW = 9_047_000_002L;
    private static final long ID_NOTIF_OLD = 9_047_000_003L;
    private static final long ID_NOTIF_NEW = 9_047_000_004L;
    private static final long ID_BACKUP_OLD = 9_047_000_005L;
    private static final long ID_BACKUP_NEW = 9_047_000_006L;
    private static final long ID_BACKUP_RUNNING_OLD = 9_047_000_007L;
    private static final long ID_BACKUP_FAILED_OLD = 9_047_000_008L;

    @Autowired
    private RetentionCleanupService retentionCleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MinioClient minioClient;

    @Value("${platform.backup.bucket:${minio.bucket}}")
    private String backupBucket;

    @Value("${platform.backup.prefix:db-backup/}")
    private String backupPrefix;

    @Test
    void prunePhysicallyDeletesExpiredRowsAndKeepsRecentAndBusinessUntouched() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime old = now.minusDays(500);   // 远超默认保留期（audit 180 / notif 90 天）
        LocalDateTime recent = now.minusDays(1);  // 各保留窗口内、均应保留
        String backupObjectKey = backupObjectKey();
        byte[] backupArtifact = "P47_BACKUP_ARTIFACT".getBytes(StandardCharsets.UTF_8);

        // 业务表不变量取样：清理绝不能碰业务表
        long fileObjectBefore = count("SELECT COUNT(*) FROM file_object");
        long sysUserBefore = count("SELECT COUNT(*) FROM sys_user");

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(backupBucket)
                    .object(backupObjectKey)
                    .stream(new ByteArrayInputStream(backupArtifact), backupArtifact.length, -1)
                    .contentType("application/octet-stream")
                    .build());
            // audit_log：过期 + 近期各一条（唯一 biz_type 标记）
            jdbcTemplate.update("INSERT INTO audit_log (id, biz_type, operation, operate_time) VALUES (?,?,?,?)",
                    ID_AUDIT_OLD, MARK, "P47", old);
            jdbcTemplate.update("INSERT INTO audit_log (id, biz_type, operation, operate_time) VALUES (?,?,?,?)",
                    ID_AUDIT_NEW, MARK, "P47", recent);
            // notification：过期 + 近期各一条（原生直插，绕过实体填充；notification 有 @TableLogic，
            // 正好验证清理走真物理删除、而非软删）
            jdbcTemplate.update(
                    "INSERT INTO notification (id, user_id, type, title, read_flag, created_at, deleted) VALUES (?,?,?,?,?,?,0)",
                    ID_NOTIF_OLD, 0L, MARK, "old", 0, old);
            jdbcTemplate.update(
                    "INSERT INTO notification (id, user_id, type, title, read_flag, created_at, deleted) VALUES (?,?,?,?,?,?,0)",
                    ID_NOTIF_NEW, 0L, MARK, "recent", 0, recent);
            // backup_record：过期终态应删，近期终态与过期运行中记录均应保留。
            jdbcTemplate.update(
                    "INSERT INTO backup_record (id, backup_type, status, storage_uri, finished_at, created_at, deleted)"
                            + " VALUES (?,?,?,?,?,?,0)",
                    ID_BACKUP_OLD, "full", "COMPLETED", "minio://" + backupBucket + "/" + backupObjectKey,
                    old, old);
            jdbcTemplate.update(
                    "INSERT INTO backup_record (id, backup_type, status, storage_uri, finished_at, created_at, deleted)"
                            + " VALUES (?,?,?,?,?,?,0)",
                    ID_BACKUP_NEW, "full", "COMPLETED", "minio://" + backupBucket + "/p47-recent.sql.gz",
                    recent, recent);
            jdbcTemplate.update(
                    "INSERT INTO backup_record (id, backup_type, status, error_message, finished_at, created_at, deleted)"
                            + " VALUES (?,?,?,?,?,?,0)",
                    ID_BACKUP_FAILED_OLD, "full", "FAILED", "P47 fixture", old, old);
            jdbcTemplate.update(
                    "INSERT INTO backup_record (id, backup_type, status, storage_uri, created_at, deleted)"
                            + " VALUES (?,?,?,?,?,0)",
                    ID_BACKUP_RUNNING_OLD, "full", "RUNNING", "minio://" + backupBucket + "/p47-running.sql.gz", old);

            int auditDeleted = retentionCleanupService.pruneAuditLog();
            int notifDeleted = retentionCleanupService.pruneNotification();
            int backupDeleted = retentionCleanupService.pruneBackupRecord();

            // 至少删了我们插入的过期行（并发下真实过期行也可能一并删，故 >=1）
            assertThat(auditDeleted).isGreaterThanOrEqualTo(1);
            assertThat(notifDeleted).isGreaterThanOrEqualTo(1);
            assertThat(backupDeleted).isGreaterThanOrEqualTo(2);

            // 过期标记行已物理删除
            assertThat(exists("audit_log", ID_AUDIT_OLD)).isFalse();
            assertThat(exists("notification", ID_NOTIF_OLD)).isFalse();
            assertThat(exists("backup_record", ID_BACKUP_OLD)).isFalse();
            assertThat(exists("backup_record", ID_BACKUP_FAILED_OLD)).isFalse();
            // 近期标记行仍在（证明按时间窗清理、非全表清空）
            assertThat(exists("audit_log", ID_AUDIT_NEW)).isTrue();
            assertThat(exists("notification", ID_NOTIF_NEW)).isTrue();
            assertThat(exists("backup_record", ID_BACKUP_NEW)).isTrue();
            // 旧的非终态记录不是归档历史，不能被保留期任务误删。
            assertThat(exists("backup_record", ID_BACKUP_RUNNING_OLD)).isTrue();
            // 记录归档不负责 MinIO 产物；旧记录删掉后对象仍必须可读取。
            assertThat(minioClient.statObject(StatObjectArgs.builder()
                    .bucket(backupBucket)
                    .object(backupObjectKey)
                    .build()).size()).isEqualTo(backupArtifact.length);

            // 业务表未被触碰
            assertThat(count("SELECT COUNT(*) FROM file_object")).isEqualTo(fileObjectBefore);
            assertThat(count("SELECT COUNT(*) FROM sys_user")).isEqualTo(sysUserBefore);
        } finally {
            try {
                jdbcTemplate.update("DELETE FROM audit_log WHERE id IN (?,?)", ID_AUDIT_OLD, ID_AUDIT_NEW);
                jdbcTemplate.update("DELETE FROM notification WHERE id IN (?,?)", ID_NOTIF_OLD, ID_NOTIF_NEW);
                jdbcTemplate.update("DELETE FROM backup_record WHERE id IN (?,?,?,?)",
                        ID_BACKUP_OLD, ID_BACKUP_NEW, ID_BACKUP_RUNNING_OLD, ID_BACKUP_FAILED_OLD);
            } finally {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(backupBucket)
                        .object(backupObjectKey)
                        .build());
            }
        }
    }

    private String backupObjectKey() {
        String prefix = backupPrefix.endsWith("/") ? backupPrefix : backupPrefix + "/";
        return prefix + "p47-retention-preserved-" + ID_BACKUP_OLD + ".bin";
    }

    private long count(String sql) {
        Long c = jdbcTemplate.queryForObject(sql, Long.class);
        return c == null ? 0L : c;
    }

    private boolean exists(String table, long id) {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Long.class, id);
        return c != null && c > 0;
    }
}
