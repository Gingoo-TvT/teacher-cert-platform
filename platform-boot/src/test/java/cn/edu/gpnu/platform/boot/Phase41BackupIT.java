package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.system.dto.BackupTriggerRequest;
import cn.edu.gpnu.platform.system.service.SystemManagementService;
import cn.edu.gpnu.platform.system.vo.BackupRecordVO;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 41.2（P0-6 真备份）：证明 triggerBackup 产出真实、可核验、可解压的备份产物并落库真实元数据。
 * 复现（旧）= 记录是谎言、无产物（storageUri=manual://…、无 size/checksum/行数）；
 * 阻断（新）= MinIO 里真有非空 gzip 对象、解压后是真实的 INSERT 行、backup_record 指向它且 COMPLETED。
 * 真实内嵌 Tomcat + 真实 MySQL + 真实 MinIO（dev 栈 tcp-minio）。自清理 MinIO 对象与 DB 行。
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
})
class Phase41BackupIT {

    @Autowired
    private SystemManagementService systemManagementService;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${platform.backup.bucket:${minio.bucket}}")
    private String bucket;

    @Test
    void triggerBackupProducesRealVerifiableArtifactInMinio() throws Exception {
        BackupTriggerRequest request = new BackupTriggerRequest();
        request.setBackupType("full");
        request.setScope("P41-IT");
        request.setRemark("P41 真备份 IT");

        BackupRecordVO vo = systemManagementService.triggerBackup(request);

        String objectKey = null;
        try {
            // 记录关联真实产物元数据（非伪造 COMPLETED）
            assertThat(vo.getStatus()).isEqualTo("COMPLETED");
            assertThat(vo.getStorageUri()).startsWith("minio://" + bucket + "/db-backup/");
            assertThat(vo.getByteSize()).isNotNull().isGreaterThan(0L);
            assertThat(vo.getChecksum()).isNotNull().startsWith("sha256:");
            assertThat(vo.getTableCount()).isNotNull().isGreaterThanOrEqualTo(30);
            assertThat(vo.getRowCount()).isNotNull().isGreaterThan(0L);
            assertThat(vo.getStorageUri()).doesNotContain("manual://");

            objectKey = vo.getStorageUri().substring(("minio://" + bucket + "/").length());

            // MinIO 对象真实存在且大小与记录一致
            long minioSize = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build()).size();
            assertThat(minioSize).isEqualTo(vo.getByteSize());

            // 下载 + 解压 → 是真实数据（包含种子权限/角色的 INSERT 行）
            String sql;
            try (InputStream raw = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
                 GZIPInputStream gz = new GZIPInputStream(raw)) {
                sql = new String(gz.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertThat(sql).startsWith("-- Teacher-Cert-Platform");
            assertThat(sql).contains("INSERT INTO `sys_permission`");
            assertThat(sql).contains("INSERT INTO `sys_role`");
            assertThat(sql).contains("-- EOF tables=" + vo.getTableCount());

            // backup_record 行真实落库（DB 侧核验，非只看返回值）
            Long persistedSize = jdbcTemplate.queryForObject(
                    "SELECT byte_size FROM backup_record WHERE id = ?", Long.class, vo.getId());
            assertThat(persistedSize).isEqualTo(vo.getByteSize());
        } finally {
            // 清理：删 MinIO 测试产物 + 硬删测试记录（避免污染验收栈）
            if (objectKey != null) {
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucket).object(objectKey).build());
                } catch (Exception ignore) {
                    // best-effort cleanup
                }
            }
            if (vo.getId() != null) {
                jdbcTemplate.update("DELETE FROM backup_record WHERE id = ?", vo.getId());
            }
        }
    }
}
