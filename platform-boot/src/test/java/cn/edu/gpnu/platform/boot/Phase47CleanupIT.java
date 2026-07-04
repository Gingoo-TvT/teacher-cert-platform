package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.system.service.impl.RetentionCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 47（P1-9 定时清理）：直接调用保留期清理服务，证明
 * 「过期行被物理删除、近期行保留、其它业务表不受影响」。
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

    @Autowired
    private RetentionCleanupService retentionCleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void prunePhysicallyDeletesExpiredRowsAndKeepsRecentAndBusinessUntouched() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime old = now.minusDays(500);   // 远超默认保留期（audit 180 / notif 90 天）
        LocalDateTime recent = now.minusDays(1);  // 两窗口内、均应保留

        // 业务表不变量取样：清理绝不能碰业务表
        long fileObjectBefore = count("SELECT COUNT(*) FROM file_object");
        long sysUserBefore = count("SELECT COUNT(*) FROM sys_user");

        try {
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

            int auditDeleted = retentionCleanupService.pruneAuditLog();
            int notifDeleted = retentionCleanupService.pruneNotification();

            // 至少删了我们插入的过期行（并发下真实过期行也可能一并删，故 >=1）
            assertThat(auditDeleted).isGreaterThanOrEqualTo(1);
            assertThat(notifDeleted).isGreaterThanOrEqualTo(1);

            // 过期标记行已物理删除
            assertThat(exists("audit_log", ID_AUDIT_OLD)).isFalse();
            assertThat(exists("notification", ID_NOTIF_OLD)).isFalse();
            // 近期标记行仍在（证明按时间窗清理、非全表清空）
            assertThat(exists("audit_log", ID_AUDIT_NEW)).isTrue();
            assertThat(exists("notification", ID_NOTIF_NEW)).isTrue();

            // 业务表未被触碰
            assertThat(count("SELECT COUNT(*) FROM file_object")).isEqualTo(fileObjectBefore);
            assertThat(count("SELECT COUNT(*) FROM sys_user")).isEqualTo(sysUserBefore);
        } finally {
            jdbcTemplate.update("DELETE FROM audit_log WHERE id IN (?,?)", ID_AUDIT_OLD, ID_AUDIT_NEW);
            jdbcTemplate.update("DELETE FROM notification WHERE id IN (?,?)", ID_NOTIF_OLD, ID_NOTIF_NEW);
        }
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
